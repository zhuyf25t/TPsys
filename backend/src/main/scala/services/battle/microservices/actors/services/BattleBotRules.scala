package services.battle.microservices.actors.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.world.services.BattleArenaCollision.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.world.services.BattleInitialLayout.*
import services.battle.microservices.actors.services.BattleInputRules.*
import services.battle.microservices.world.services.BattleMotionRules.*
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.actors.objects.actors.BattleBotRuleConfig
import services.battle.microservices.combat.services.BattleWeaponRules.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.abilities.objects.pickup.PickupKind
import services.battle.objects.core.{BattleAggregateState, BattleMapId, BattleVector2, FacingRadians, SpawnPointIndex}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.actors.objects.player.BattlePlayerState

private[battle] object BattleBotRules {
  private val WinterZombieMapId: BattleMapId = BattleMapId("winter-hunt-v1")
  private type BotControlResolver =
    (BattlePlayerState, BattleAggregateState, BattleArenaContext, BattleDynamicRuleBook) => IO[BattlePlayerState]

  private val BotControlResolvers: Map[BattleMapId, BotControlResolver] =
    Map(WinterZombieMapId -> applyZombieControl)

  private final case class BotTarget(
    player: BattlePlayerState,
    distance: Double,
    visible: Boolean
  )

  def applyBotControl(
    player: BattlePlayerState,
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    if player.isBot then botControlResolver(state.mapId)(player, state, arena, battleRules)
    else IO.pure(player)
  }

  private def botControlResolver(mapId: BattleMapId): BotControlResolver =
    BotControlResolvers.getOrElse(mapId, applyCombatBotControl)

  private def applyCombatBotControl(
    player: BattlePlayerState,
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    for
      botRules <- battleRules.bot
      target <- selectTarget(player, state, arena)
      reloadPressed <- shouldBotReload(player, target.exists(_.visible), botRules, battleRules)
      movement <-
        if reloadPressed then reloadMovement(player, target, state, botRules, arena, battleRules)
        else target match {
          case Some(botTarget) => combatMovement(player, botTarget, state, botRules, arena)
          case None            => objectiveMovement(player, state, botRules, arena, battleRules)
        }
      aim <-
        target match {
          case Some(botTarget) => aimAtTarget(player, botTarget, state, botRules)
          case None =>
            vectorLength(movement).flatMap { movementLength =>
              normalizeAim(player.aim, if movementLength > 0.0001 then movement else player.aim)
            }
        }
      primaryHeld <-
        if reloadPressed then IO.pure(false)
        else target.traverse(shouldFireAtTarget(player, _, state, botRules, battleRules)).map(_.getOrElse(false))
    yield player.copy(
      aim = aim,
      facing = FacingRadians(math.atan2(aim.y, aim.x)),
      movement = movement,
      sprint = false,
      primaryHeld = primaryHeld,
      reloadPressed = reloadPressed
    )
  }

  private def applyZombieControl(
    player: BattlePlayerState,
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    for
      botRules <- battleRules.bot
      target <- selectZombieTarget(player, state)
      movement <- target match {
        case Some(botTarget) =>
          subtract(botTarget.player.position, player.position)
            .flatMap(direction => chooseOpenMovement(player, direction, botRules, arena))
        case None =>
          objectiveMovement(player, state, botRules, arena, battleRules)
      }
      aim <-
        target match {
          case Some(botTarget) =>
            subtract(botTarget.player.position, player.position)
              .flatMap(direction => normalizeAim(player.aim, direction))
          case None =>
            vectorLength(movement).flatMap { movementLength =>
              normalizeAim(player.aim, if movementLength > 0.0001 then movement else player.aim)
            }
        }
    yield player.copy(
      aim = aim,
      facing = FacingRadians(math.atan2(aim.y, aim.x)),
      movement = movement,
      sprint = false,
      primaryHeld = false,
      reloadPressed = false
    )
  }

  private def selectZombieTarget(player: BattlePlayerState, state: BattleAggregateState): IO[Option[BotTarget]] =
    state.players
      .filter(candidate => candidate.playerId != player.playerId && candidate.alive && !candidate.isBot)
      .traverse(candidate => distanceBetween(player.position, candidate.position).map(distance =>
        BotTarget(
          player = candidate,
          distance = distance,
          visible = true
        )
      ))
      .map(_.sortBy(_.distance).headOption)

  private def selectTarget(player: BattlePlayerState, state: BattleAggregateState, arena: BattleArenaContext): IO[Option[BotTarget]] =
    state.players
      .filter(candidate => candidate.playerId != player.playerId && candidate.alive)
      .traverse { candidate =>
        for
          visible <- hasArenaLineOfSight(player.position, candidate.position, arena)
          distance <- distanceBetween(player.position, candidate.position)
        yield
          BotTarget(
            player = candidate,
            distance = distance,
            visible = visible
          )
      }
      .flatMap { targets =>
        val visibleTargets = targets.filter(_.visible)
        val visibleOrAll = if visibleTargets.nonEmpty then visibleTargets else targets
        val humanTargets = visibleOrAll.filterNot(_.player.isBot)
        val preferredTargets = if humanTargets.nonEmpty then humanTargets else visibleOrAll

        preferredTargets
          .traverse(target => targetScore(target).map(score => target -> score))
          .map(_.sortBy { case (_, score) => score }.headOption.map { case (target, _) => target })
      }

  private def targetScore(target: BotTarget): IO[Double] = IO.pure {
    val visibilityPenalty = if target.visible then 0.0 else 380.0
    val humanPenalty = if target.player.isBot then 120.0 else 0.0
    val lowHealthBonus = (target.player.maxHp.value - target.player.hp.value) * -1.6
    target.distance + visibilityPenalty + humanPenalty + lowHealthBonus
  }

  private def aimAtTarget(
    player: BattlePlayerState,
    target: BotTarget,
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig
  ): IO[BattleVector2] = {
    val wobbleDirection =
      if ((state.tick.value + player.seat.value.toLong + target.player.seat.value.toLong) % 2L) == 0L then 1.0
      else -1.0
    for
      lead <- scale(target.player.movement, botRules.aimLeadDistance.value)
      base <- subtract(target.player.position, player.position)
      normalizedBase <- normalizeAim(player.aim, base)
      perpendicularBase <- perpendicular(normalizedBase, wobbleDirection)
      aimNoise <- scale(perpendicularBase, botRules.aimErrorRadius.value)
      aimBase <- add(base, lead)
      noisyAim <- add(aimBase, aimNoise)
      aim <- normalizeAim(player.aim, noisyAim)
    yield aim
  }

  private def combatMovement(
    player: BattlePlayerState,
    target: BotTarget,
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig,
    arena: BattleArenaContext
  ): IO[BattleVector2] = {
    for
      toTarget <- subtract(target.player.position, player.position)
      aim <- normalizeAim(player.aim, toTarget)
      retreat <- shouldRetreat(player, target, botRules)
      desired <-
        if retreat then coverOrRetreatDirection(player, target, botRules, arena)
        else if !target.visible then flankDirection(player, aim, state)
        else
          for
            orbitDirectionValue <- orbitDirection(player, state)
            orbit <- perpendicular(aim, orbitDirectionValue)
            radial <-
              if target.distance > botRules.preferredRange.value + botRules.preferredRangeAdvanceMargin.value then IO.pure(aim)
              else if target.distance < botRules.preferredRange.value - botRules.preferredRangeRetreatMargin.value then scale(aim, -1.0)
              else IO.pure(BattleArenaContext.ZeroVector)
            scaledRadial <- scale(radial, 0.86)
            scaledOrbit <- scale(orbit, 0.52)
            combined <- add(scaledRadial, scaledOrbit)
          yield combined
      movement <- chooseOpenMovement(player, desired, botRules, arena)
    yield movement
  }

  private def reloadMovement(
    player: BattlePlayerState,
    target: Option[BotTarget],
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleVector2] =
    target match {
      case Some(botTarget) =>
        coverOrRetreatDirection(player, botTarget, botRules, arena)
          .flatMap(direction => chooseOpenMovement(player, direction, botRules, arena))
      case None            => objectiveMovement(player, state, botRules, arena, battleRules)
    }

  private def objectiveMovement(
    player: BattlePlayerState,
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleVector2] =
    pickupObjective(player, state, botRules, battleRules).flatMap {
      case Some(pickup) =>
        subtract(pickup.position, player.position)
          .flatMap(direction => chooseOpenMovement(player, direction, botRules, arena))
      case None =>
        for
          target <- patrolTarget(player, state, arena, battleRules)
          direction <- subtract(target, player.position)
          movement <- chooseOpenMovement(player, direction, botRules, arena)
        yield movement
    }

  private def pickupObjective(
    player: BattlePlayerState,
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig,
    battleRules: BattleDynamicRuleBook
  ): IO[Option[BattlePickupState]] = {
    val availablePickups = state.pickups.filter(_.available)
    val needsHealth = player.hp.value <= player.maxHp.value * botRules.pickupHealthRatio
    for
      current <- currentWeapon(player)
      needsAmmo <- current match {
        case None =>
          IO.pure(false)
        case Some(weapon) =>
          weaponUsesHeat(weapon.weaponKind, battleRules).map { usesHeat =>
            !usesHeat &&
              weapon.ammoInMagazine.value <= math.max(1, math.floor(weapon.magazineSize.value * botRules.tacticalReloadRatio).toInt)
          }
      }
      pickupDistances <- availablePickups.traverse(pickup =>
        distanceBetween(player.position, pickup.position).map(distance => pickup -> distance)
      )
    yield {
      val needsWeapon = current.forall(_.weaponKind == WeaponKind.Pistol) || needsAmmo
      val preferred =
        if needsHealth then pickupDistances.filter { case (pickup, _) => pickup.pickupKind == PickupKind.Medkit }
        else if needsWeapon then pickupDistances.filter { case (pickup, _) => pickup.pickupKind == PickupKind.Weapon }
        else pickupDistances.filter { case (_, distance) => distance <= botRules.pickupSeekRange.value }

      preferred.sortBy { case (_, distance) => distance }.headOption.map { case (pickup, _) => pickup }
    }
  }

  private def shouldRetreat(player: BattlePlayerState, target: BotTarget, botRules: BattleBotRuleConfig): IO[Boolean] = IO.pure {
    val lowHealth = player.hp.value <= player.maxHp.value * botRules.lowHealthRatio
    lowHealth || target.distance < botRules.preferredRange.value - botRules.preferredRangeRetreatMargin.value
  }

  private def coverOrRetreatDirection(
    player: BattlePlayerState,
    target: BotTarget,
    botRules: BattleBotRuleConfig,
    arena: BattleArenaContext
  ): IO[BattleVector2] =
    for
      awayVector <- subtract(player.position, target.player.position)
      away <- normalizeMovement(awayVector)
      awayForward <- scale(away, 0.82)
      positivePerpendicular <- perpendicular(away, 1.0)
      negativePerpendicular <- perpendicular(away, -1.0)
      positiveOffset <- scale(positivePerpendicular, 0.58)
      negativeOffset <- scale(negativePerpendicular, 0.58)
      positiveCandidate <- add(awayForward, positiveOffset)
      negativeCandidate <- add(awayForward, negativeOffset)
      candidates <- Vector(
        away,
        positiveCandidate,
        negativeCandidate,
        positivePerpendicular,
        negativePerpendicular
      ).traverse(normalizeMovement)
      scoredOptions <- candidates.traverse { direction =>
        vectorLength(direction).flatMap { length =>
          if length <= 0.0001 then IO.pure(None)
          else
            for
              probeOffset <- scale(direction, botRules.coverProbeDistance.value)
              unclampedProbe <- add(player.position, probeOffset)
              probe <- clampToPlayable(unclampedProbe, arena)
              lineOfSight <- hasArenaLineOfSight(target.player.position, probe, arena)
              canOccupy <- canPlayerOccupy(probe, arena.playerCollisionRadius, arena)
              probeDistance <- distanceBetween(probe, target.player.position)
            yield
              val coverBonus = if lineOfSight then 0.0 else 900.0
              val occupancyBonus = if canOccupy then 180.0 else -900.0
              Some(direction -> (coverBonus + occupancyBonus + probeDistance))
        }
      }
    yield
      scoredOptions.flatten
        .maxByOption { case (_, score) => score }
        .map { case (direction, _) => direction }
        .getOrElse(away)

  private def flankDirection(player: BattlePlayerState, aim: BattleVector2, state: BattleAggregateState): IO[BattleVector2] =
    for
      scaledAim <- scale(aim, 0.58)
      orbitDirectionValue <- orbitDirection(player, state)
      orbit <- perpendicular(aim, orbitDirectionValue)
      scaledOrbit <- scale(orbit, 0.92)
      combined <- add(scaledAim, scaledOrbit)
      movement <- normalizeMovement(combined)
    yield movement

  private def chooseOpenMovement(
    player: BattlePlayerState,
    desired: BattleVector2,
    botRules: BattleBotRuleConfig,
    arena: BattleArenaContext
  ): IO[BattleVector2] =
    for
      normalized <- normalizeMovement(desired)
      normalizedLength <- vectorLength(normalized)
      movement <-
        if normalizedLength <= 0.0001 then IO.pure(BattleArenaContext.ZeroVector)
        else {
          for
            reverse <- scale(normalized, -1.0)
            positiveSmall <- rotate(normalized, math.Pi / 6.0)
            negativeSmall <- rotate(normalized, -math.Pi / 6.0)
            positiveMedium <- rotate(normalized, math.Pi / 3.0)
            negativeMedium <- rotate(normalized, -math.Pi / 3.0)
            positiveSide <- rotate(normalized, math.Pi / 2.0)
            negativeSide <- rotate(normalized, -math.Pi / 2.0)
            candidates = Vector(normalized, positiveSmall, negativeSmall, positiveMedium, negativeMedium, positiveSide, negativeSide, reverse)
            normalizedCandidates <- candidates.traverse(normalizeMovement)
            scoredCandidates <- normalizedCandidates.traverse { direction =>
              findMotionDestination(
                position = player.position,
                direction = direction,
                distance = botRules.movementProbeDistance.value,
                radius = arena.playerCollisionRadius,
                arena = arena
              ).flatMap(motion => distanceBetween(player.position, motion.destination).map(distance => direction -> distance))
            }
            fallbackVector <- perpendicular(player.aim, 1.0)
            fallback <- normalizeMovement(fallbackVector)
          yield scoredCandidates
            .filter { case (_, distance) => distance > 4.0 }
            .maxByOption { case (_, distance) => distance }
            .map(_._1)
            .getOrElse(fallback)
        }
    yield movement

  private def shouldFireAtTarget(
    player: BattlePlayerState,
    target: BotTarget,
    state: BattleAggregateState,
    botRules: BattleBotRuleConfig,
    battleRules: BattleDynamicRuleBook
  ): IO[Boolean] =
    val weaponCanFireIO =
      currentWeapon(player).flatMap {
        case None =>
          IO.pure(false)
        case Some(weapon) =>
          weaponUsesHeat(weapon.weaponKind, battleRules).map { usesHeat =>
            weapon.reloadRemainingMs.value <= 0 &&
              (usesHeat || weapon.ammoInMagazine.value > 0)
          }
      }

    for
      weaponCanFire <- weaponCanFireIO
      fireRange <- botFireRangeForTarget(target.player, botRules)
      allowedByOpeningDelay <- canBotFireAtTarget(state, botRules)
      firePulseOpen <- isBotFirePulseOpen(player, state, botRules)
    yield
      target.visible &&
      target.distance <= fireRange &&
      allowedByOpeningDelay &&
      firePulseOpen &&
      weaponCanFire

  private def shouldBotReload(
    player: BattlePlayerState,
    visibleThreat: Boolean,
    botRules: BattleBotRuleConfig,
    battleRules: BattleDynamicRuleBook
  ): IO[Boolean] =
    if !player.isBot then IO.pure(false)
    else
      currentWeapon(player).flatMap {
        case None =>
          IO.pure(false)
        case Some(weapon) =>
          for
            canStart <- canStartMagazineReload(weapon, battleRules)
            usesHeat <- weaponUsesHeat(weapon.weaponKind, battleRules)
          yield
            val emptyReload = weapon.ammoInMagazine.value <= 0 && canStart
            val tacticalReload =
              !visibleThreat &&
                !usesHeat &&
                weapon.ammoInMagazine.value <= math.max(1, math.floor(weapon.magazineSize.value * botRules.tacticalReloadRatio).toInt) &&
                canStart
            emptyReload || tacticalReload
      }

  private def canBotFireAtTarget(state: BattleAggregateState, botRules: BattleBotRuleConfig): IO[Boolean] =
    IO.pure(state.elapsedMs.value >= botRules.openingFireDelay.value)

  private def isBotFirePulseOpen(player: BattlePlayerState, state: BattleAggregateState, botRules: BattleBotRuleConfig): IO[Boolean] = IO.pure {
    val interval = math.max(1L, botRules.firePulseInterval.value)
    val window = math.max(1L, math.min(botRules.firePulseWindow.value, interval))
    val rawPhase = (state.elapsedMs.value + player.seat.value.toLong * 97L) % interval
    val phase = if rawPhase < 0L then rawPhase + interval else rawPhase
    phase < window
  }

  private def botFireRangeForTarget(target: BattlePlayerState, botRules: BattleBotRuleConfig): IO[Double] =
    IO.pure {
      if target.isBot then botRules.botFireRange.value
      else botRules.humanFireRange.value
    }

  private def patrolTarget(
    player: BattlePlayerState,
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleVector2] =
    spawnPointFor(state.mapId, SpawnPointIndex(player.seat.value), battleRules).flatMap { spawnAnchor =>
      val patrolAngle = (state.tick.value + player.seat.value.toLong * 11L).toDouble * 0.18
      clampToPlayable(BattleVector2(
        spawnAnchor.x + math.cos(patrolAngle) * 260.0,
        spawnAnchor.y + math.sin(patrolAngle) * 190.0
      ), arena)
    }

  private def orbitDirection(player: BattlePlayerState, state: BattleAggregateState): IO[Double] =
    IO.pure {
      if (state.tick.value + player.seat.value.toLong) % 2L == 0L then 1.0
      else -1.0
    }

  private def rotate(vector: BattleVector2, radians: Double): IO[BattleVector2] =
    IO.pure(BattleVector2(
      vector.x * math.cos(radians) - vector.y * math.sin(radians),
      vector.x * math.sin(radians) + vector.y * math.cos(radians)
    ))

  private def clampToPlayable(point: BattleVector2, arena: BattleArenaContext): IO[BattleVector2] =
    for
      x <- clampDouble(
        point.x,
        arena.playerCollisionRadius,
        arena.worldSize.x - arena.playerCollisionRadius
      )
      y <- clampDouble(
        point.y,
        arena.playerCollisionRadius,
        arena.worldSize.y - arena.playerCollisionRadius
      )
    yield BattleVector2(x, y)
}
