package services.battle.microservices.extraction.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.actors.objects.player.{BattlePlayerLifeState, BattlePlayerState, HitPoints, Score}
import services.battle.microservices.actors.services.BattlePlayerLifecycleRules
import services.battle.microservices.extraction.objects.extraction.{
  BattleExtractionInterruptReason,
  BattleExtractionProgressMillis,
  BattleExtractionState,
  BattleExtractionStatus,
  BattleExtractionZoneDefinition,
  BattleExtractionZoneId,
  BattleGasDamagePerSecond,
  BattleGasPhase,
  BattleGasPlanDefinition,
  BattleGasStageDefinition,
  BattleGasZoneState,
  BattleLootCacheState,
  BattleLootCacheStatus,
  BattleLootSearchProgressMillis
}
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.runtime.services.BattleTimeRules
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.world.services.BattleGeometry.distanceBetween
import services.battle.objects.core.{BattleAggregateState, DurationMillis, ElapsedMillis, Radius}

private[battle] object BattleExtractionRuntimeRules {
  def advanceObjectives(
    state: BattleAggregateState,
    deltaMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    for
      arena <- BattleArenaCatalog.contextFor(state.mapId, battleRules)
      withGas <- advanceGasZone(state, deltaMs, arena)
      afterGasDamage <- applyGasDamage(withGas, deltaMs)
      afterLoot <- advanceLootCaches(afterGasDamage, deltaMs)
      afterExtraction <- advanceExtraction(afterLoot, deltaMs)
    yield afterExtraction

  def extractedWinner(state: BattleAggregateState): IO[Option[BattlePlayerState]] =
    IO.pure(state.extraction.flatMap(_.status match {
      case BattleExtractionStatus.Extracted(playerId, _, _, _) =>
        state.players.find(_.playerId == playerId)
      case _ =>
        None
    }))

  def hasExtracted(state: BattleAggregateState): IO[Boolean] =
    IO.pure(state.extraction.exists(_.status match {
      case BattleExtractionStatus.Extracted(_, _, _, _) => true
      case _                                            => false
    }))

  private def advanceGasZone(state: BattleAggregateState, deltaMs: Long, arena: BattleArenaContext): IO[BattleAggregateState] =
    arena.gasPlan match {
      case None =>
        IO.pure(state.copy(gasZone = None))
      case Some(plan) =>
        gasZoneAt(plan, state.elapsedMs.value, deltaMs).map(gasZone => state.copy(gasZone = gasZone))
    }

  private def gasZoneAt(plan: BattleGasPlanDefinition, elapsedMs: Long, deltaMs: Long): IO[Option[BattleGasZoneState]] =
    plan.stages.headOption match {
      case None =>
        IO.pure(None)
      case Some(firstStage) =>
        activeGasStage(plan.stages, elapsedMs).map { activeStage =>
          val stage = activeStage.getOrElse(firstStage)
          val stageStart = stage.startsAt.value
          val stageEnd = stage.startsAt.value + stage.duration.value
          val progress = math.max(0L, math.min(stage.duration.value, elapsedMs - stageStart))
          val ratio =
            if elapsedMs < stageStart then 0.0
            else if stage.duration.value <= 0L then 1.0
            else progress.toDouble / stage.duration.value.toDouble
          val radius = Radius(stage.fromRadius.value + ((stage.toRadius.value - stage.fromRadius.value) * ratio))
          val phase =
            if elapsedMs < stageStart then BattleGasPhase.Waiting
            else if stage.toRadius.value <= 0.0 && elapsedMs >= stageEnd then BattleGasPhase.Final
            else BattleGasPhase.Advancing
          val dps =
            if elapsedMs < stageStart || deltaMs <= 0L then BattleGasDamagePerSecond(0.0)
            else stage.damagePerSecond

          Some(BattleGasZoneState(
            phase = phase,
            center = plan.center,
            radius = radius,
            nextRadius = stage.toRadius,
            damagePerSecond = dps,
            stageIndex = stage.stageIndex,
            progressMs = DurationMillis(progress),
            startsAt = stage.startsAt,
            endsAt = ElapsedMillis(stageEnd)
          ))
        }
    }

  private def activeGasStage(stages: Vector[BattleGasStageDefinition], elapsedMs: Long): IO[Option[BattleGasStageDefinition]] =
    IO.pure(
      stages
        .find(stage => elapsedMs >= stage.startsAt.value && elapsedMs <= stage.startsAt.value + stage.duration.value)
        .orElse(stages.filter(stage => elapsedMs > stage.startsAt.value + stage.duration.value).lastOption)
    )

  private def applyGasDamage(state: BattleAggregateState, deltaMs: Long): IO[BattleAggregateState] =
    state.gasZone match {
      case Some(zone) if zone.damagePerSecond.value > 0.0 && deltaMs > 0L =>
        val previousElapsed = math.max(0L, state.elapsedMs.value - deltaMs)
        BattleTimeRules.elapsedRateDelta(zone.damagePerSecond.value, previousElapsed, state.elapsedMs.value).flatMap { damage =>
          if damage <= 0 then IO.pure(state)
          else state.players.traverse(player => damagePlayerOutsideGas(player, zone, damage, state.elapsedMs)).map(players => state.copy(players = players))
        }
      case _ =>
        IO.pure(state)
    }

  private def damagePlayerOutsideGas(
    player: BattlePlayerState,
    zone: BattleGasZoneState,
    damage: Int,
    elapsedMs: ElapsedMillis
  ): IO[BattlePlayerState] =
    if !player.alive || player.hp.value <= 0 then IO.pure(player)
    else
      distanceBetween(player.position, zone.center).flatMap { distance =>
        if distance <= zone.radius.value then IO.pure(player)
        else {
          val hpAfter = math.max(0, player.hp.value - damage)
          if hpAfter <= 0 then
            BattlePlayerLifecycleRules.clearDeadPlayerRuntime(
              player.copy(
                hp = HitPoints(0),
                lifeState = BattlePlayerLifeState.eliminated(player.eliminatedAtMs.orElse(Some(elapsedMs)), DurationMillis(0L))
              )
            )
          else IO.pure(player.copy(hp = HitPoints(hpAfter)))
        }
      }

  private def advanceLootCaches(state: BattleAggregateState, deltaMs: Long): IO[BattleAggregateState] =
    state.lootCaches.foldLeft(IO.pure(state -> Vector.empty[BattleLootCacheState])) { case (previous, cache) =>
      for
        current <- previous
        (currentState, caches) = current
        updated <- advanceLootCache(currentState, cache, deltaMs)
        (updatedState, updatedCache) = updated
      yield updatedState -> (caches :+ updatedCache)
    }.map { case (nextState, nextCaches) => nextState.copy(lootCaches = nextCaches) }

  private def advanceLootCache(
    state: BattleAggregateState,
    cache: BattleLootCacheState,
    deltaMs: Long
  ): IO[(BattleAggregateState, BattleLootCacheState)] =
    cache.status match {
      case BattleLootCacheStatus.Available =>
        searcherInRange(state, cache).flatMap {
          case Some(player) =>
            cacheSearchStatus(player, deltaMs).map(status => state -> cache.copy(status = status))
          case None =>
            IO.pure(state -> cache)
        }

      case BattleLootCacheStatus.Searching(playerId, _, progressMs) =>
        state.players.find(_.playerId == playerId) match {
          case Some(player) =>
            for
              usable <- canUseObjective(player)
              inRange <- inCacheRange(player, cache)
              result <-
                if usable && inRange then
                  val nextProgress = progressMs.value + math.max(0L, deltaMs)
                  if nextProgress >= cache.searchDuration.value then
                    grantLootScore(state, player, cache.scoreValue.value).map { updatedState =>
                      updatedState -> cache.copy(status = BattleLootCacheStatus.Searched(player.playerId, player.heroId, state.elapsedMs))
                    }
                  else cacheSearchStatus(player, nextProgress).map(status => state -> cache.copy(status = status))
                else IO.pure(state -> cache.copy(status = BattleLootCacheStatus.Available))
            yield result
          case _ =>
            IO.pure(state -> cache.copy(status = BattleLootCacheStatus.Available))
        }

      case BattleLootCacheStatus.Searched(_, _, _) =>
        IO.pure(state -> cache)
    }

  private def cacheSearchStatus(player: BattlePlayerState, progressMs: Long): IO[BattleLootCacheStatus] =
    IO.pure(BattleLootCacheStatus.Searching(
      playerId = player.playerId,
      heroId = player.heroId,
      progressMs = BattleLootSearchProgressMillis(math.max(0L, progressMs))
    ))

  private def grantLootScore(state: BattleAggregateState, player: BattlePlayerState, scoreValue: Int): IO[BattleAggregateState] =
    IO.pure(state.copy(players = state.players.map {
      case existing if existing.playerId == player.playerId =>
        existing.copy(score = Score(existing.score.value + math.max(0, scoreValue)))
      case existing =>
        existing
    }))

  private def advanceExtraction(state: BattleAggregateState, deltaMs: Long): IO[BattleAggregateState] =
    state.extraction match {
      case None =>
        IO.pure(state)
      case Some(extraction) =>
        nextExtractionStatus(state, extraction, deltaMs).map(status => state.copy(extraction = Some(extraction.copy(status = status))))
    }

  private def nextExtractionStatus(
    state: BattleAggregateState,
    extraction: BattleExtractionState,
    deltaMs: Long
  ): IO[BattleExtractionStatus] =
    for
      zones <- availableExtractionZones(extraction, state.elapsedMs.value)
      status <- extraction.status match {
        case BattleExtractionStatus.Extracted(_, _, _, _) =>
          IO.pure(extraction.status)

        case BattleExtractionStatus.Extracting(playerId, _, zoneId, progressMs) =>
          val maybeZone = zones.find(_.zoneId == zoneId)
          val maybePlayer = state.players.find(_.playerId == playerId)
          (maybePlayer, maybeZone) match {
            case (Some(player), Some(zone)) =>
              for
                usable <- canUseObjective(player)
                inZone <- inExtractionZone(player, zone)
                status <-
                  if usable && inZone then
                    val nextProgress = progressMs.value + math.max(0L, deltaMs)
                    if nextProgress >= zone.channelDuration.value then
                      IO.pure(BattleExtractionStatus.Extracted(player.playerId, player.heroId, zone.zoneId, state.elapsedMs))
                    else
                      IO.pure(BattleExtractionStatus.Extracting(
                        player.playerId,
                        player.heroId,
                        zone.zoneId,
                        BattleExtractionProgressMillis(nextProgress)
                      ))
                  else if !player.alive || player.hp.value <= 0 then
                    interrupted(player, zone.zoneId, BattleExtractionInterruptReason.Eliminated, state.elapsedMs)
                  else interrupted(player, zoneId, BattleExtractionInterruptReason.LeftZone, state.elapsedMs)
              yield status
            case (Some(player), _) =>
              interrupted(player, zoneId, BattleExtractionInterruptReason.LeftZone, state.elapsedMs)
            case _ =>
              IO.pure(BattleExtractionStatus.Available)
          }

        case _ =>
          if zones.isEmpty then IO.pure(BattleExtractionStatus.Inactive)
          else
            playerInExtractionZone(state, zones).flatMap {
              case Some((player, zone)) =>
                val nextProgress = math.max(0L, deltaMs)
                if nextProgress >= zone.channelDuration.value then
                  IO.pure(BattleExtractionStatus.Extracted(player.playerId, player.heroId, zone.zoneId, state.elapsedMs))
                else
                  IO.pure(BattleExtractionStatus.Extracting(
                    player.playerId,
                    player.heroId,
                    zone.zoneId,
                    BattleExtractionProgressMillis(nextProgress)
                  ))
              case None =>
                IO.pure(BattleExtractionStatus.Available)
            }
        }
    yield status

  private def interrupted(
    player: BattlePlayerState,
    zoneId: BattleExtractionZoneId,
    reason: BattleExtractionInterruptReason,
    elapsedMs: ElapsedMillis
  ): IO[BattleExtractionStatus] =
    IO.pure(BattleExtractionStatus.Interrupted(player.playerId, player.heroId, zoneId, reason, elapsedMs))

  private def availableExtractionZones(
    extraction: BattleExtractionState,
    elapsedMs: Long
  ): IO[Vector[BattleExtractionZoneDefinition]] =
    IO.pure(extraction.zones.filter(zone => elapsedMs >= zone.availableFrom.value))

  private def playerInExtractionZone(
    state: BattleAggregateState,
    zones: Vector[BattleExtractionZoneDefinition]
  ): IO[Option[(BattlePlayerState, BattleExtractionZoneDefinition)]] =
    state.players.foldM(Option.empty[(BattlePlayerState, BattleExtractionZoneDefinition)]) {
      case (found @ Some(_), _) =>
        IO.pure(found)
      case (None, player) =>
        canUseObjective(player).flatMap {
          case false =>
            IO.pure(None)
          case true =>
            zones.findM(zone => inExtractionZone(player, zone)).map(_.map(zone => player -> zone))
        }
    }

  private def searcherInRange(state: BattleAggregateState, cache: BattleLootCacheState): IO[Option[BattlePlayerState]] =
    state.players.findM { player =>
      canUseObjective(player).flatMap {
        case false => IO.pure(false)
        case true  => inCacheRange(player, cache)
      }
    }

  private def canUseObjective(player: BattlePlayerState): IO[Boolean] =
    IO.pure(player.alive && player.hp.value > 0 && !player.isBot)

  private def inExtractionZone(player: BattlePlayerState, zone: BattleExtractionZoneDefinition): IO[Boolean] =
    distanceBetween(player.position, zone.position).map(_ <= zone.radius.value)

  private def inCacheRange(player: BattlePlayerState, cache: BattleLootCacheState): IO[Boolean] =
    distanceBetween(player.position, cache.position).map(_ <= cache.radius.value)
}
