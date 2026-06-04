package services.battle.microservices.combat.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.runtime.services.BattleEventFactory.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.actors.services.BattlePlayerLifecycleRules.*
import services.battle.microservices.combat.services.BattleProjectileTerminalRules.*
import services.battle.microservices.runtime.objects.runtime.BattleHistoryCount
import services.battle.microservices.combat.objects.projectile.{ProjectileKind, ProjectileTerminalReason}
import services.battle.microservices.runtime.objects.event.{BattleEventKind, BattleEventState}
import services.battle.objects.core.{BattleAggregateState, BattleVector2, DurationMillis}
import services.battle.microservices.actors.objects.player.{BattlePlayerLifeState, BattlePlayerState, HitPoints, KillCount, Score}
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileImpactRules {
  def applyProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState],
    playerCollisionRadius: Double,
    retainedProjectileTerminalCount: BattleHistoryCount,
    retainedBattleEventCount: BattleHistoryCount
  ): IO[BattleAggregateState] =
    if projectile.projectileKind == ProjectileKind.Rocket && projectile.splashRadius.value > 0.0 then
      applyRocketProjectileImpact(state, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, directTarget, playerCollisionRadius, retainedProjectileTerminalCount, retainedBattleEventCount)
    else
      for
        damaged <- directTarget match {
          case Some(target) => damageProjectileTarget(state, projectile, target, retainedBattleEventCount)
          case None         => IO.pure(state -> None)
        }
        (damagedState, report) = damaged
        terminal <- terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, report)
        nextState <- appendProjectileTerminal(
          damagedState,
          terminal,
          retainedProjectileTerminalCount
        )
      yield nextState

  private def applyRocketProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState],
    playerCollisionRadius: Double,
    retainedProjectileTerminalCount: BattleHistoryCount,
    retainedBattleEventCount: BattleHistoryCount
  ): IO[BattleAggregateState] = {
    val splashRadius = projectile.splashRadius.value + playerCollisionRadius

    for
      splashTargets <- state.players
        .filter(player => player.alive && player.heroId != projectile.ownerHeroId)
        .traverse(player => distanceBetween(player.position, terminalPosition).map(distance => player -> distance))
        .map { distances =>
          distances
            .filter { case (_, distance) => distance <= splashRadius }
            .sortBy { case (player, distance) =>
              if directTarget.exists(_.playerId == player.playerId) then -1.0
              else distance
            }
            .map { case (player, _) => player }
        }
      damaged <- splashTargets.foldLeft(IO.pure(state -> Vector.empty[ProjectileDamageReport])) {
        case (previous, target) =>
          for
            current <- previous
            (currentState, currentReports) = current
            next <- currentState.players.find(_.playerId == target.playerId) match {
              case Some(player) if player.alive =>
                damageProjectileTarget(currentState, projectile, player, retainedBattleEventCount).map {
                  case (nextState, report) => nextState -> (currentReports ++ report)
                }
              case _ =>
                IO.pure(currentState -> currentReports)
            }
          yield next
      }
      (damagedState, reports) = damaged
      terminal <- terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, reports.headOption)
      nextState <- appendProjectileTerminal(
        damagedState,
        terminal,
        retainedProjectileTerminalCount
      )
    yield nextState
  }

  private def damageProjectileTarget(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    target: BattlePlayerState,
    retainedBattleEventCount: BattleHistoryCount
  ): IO[(BattleAggregateState, Option[ProjectileDamageReport])] =
    if !target.alive || target.heroId == projectile.ownerHeroId then IO.pure(state -> None)
    else {
      val hpBefore = target.hp
      for
        damage <- BattleCriticalDamageRules.projectileDamage(
          projectile.damage,
          state.players.find(_.heroId == projectile.ownerHeroId)
        )
        hpAfterValue = math.max(0, target.hp.value - damage.value)
        eliminated = hpAfterValue <= 0
        damagedTarget <- {
          if eliminated then
            clearDeadPlayerRuntime(target.copy(
              hp = HitPoints(0),
              lifeState = BattlePlayerLifeState.eliminated(
                target.eliminatedAtMs.orElse(Some(state.elapsedMs)),
                DurationMillis(0L)
              )
            ))
          else IO.pure(target.copy(hp = HitPoints(hpAfterValue)))
        }
        creditedOwner = state.players.find(_.heroId == projectile.ownerHeroId).map { owner =>
          if eliminated then owner.copy(score = Score(owner.score.value + 1), kills = KillCount(owner.kills.value + 1))
          else owner
        }
        updatedPlayers = state.players.map { player =>
          if player.playerId == damagedTarget.playerId then damagedTarget
          else creditedOwner.filter(_.playerId == player.playerId).getOrElse(player)
        }
        stateWithPlayers = state.copy(players = updatedPlayers)
        stateWithEvents <-
          if eliminated then
            creditedOwner match {
              case Some(owner) =>
                for
                  event <- battleEvent(stateWithPlayers, BattleEventKind.Kill, owner, damagedTarget)
                  retainedEvents <- retainRecentEvents(stateWithPlayers.events :+ event, retainedBattleEventCount)
                yield stateWithPlayers.copy(events = retainedEvents)
              case None => IO.pure(stateWithPlayers)
            }
          else IO.pure(stateWithPlayers)
      yield stateWithEvents -> Some(ProjectileDamageReport(target.copy(hp = hpBefore), damagedTarget, damage))
    }

  private def retainRecentEvents(
    events: Vector[BattleEventState],
    retainedCount: BattleHistoryCount
  ): IO[Vector[BattleEventState]] =
    IO.pure(events.takeRight(retainedCount.value))
}
