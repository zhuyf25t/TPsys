package services.battle.microservices.combat.services

import services.battle.microservices.runtime.services.BattleEventFactory.*
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.actors.services.BattlePlayerLifecycleRules.*
import services.battle.microservices.combat.services.BattleProjectileTerminalRules.*
import services.battle.microservices.runtime.objects.runtime.BattleHistoryCount
import services.battle.microservices.combat.objects.projectile.{ProjectileKind, ProjectileTerminalReason}
import services.battle.microservices.runtime.objects.event.BattleEventKind
import services.battle.objects.core.{BattleAggregateState, BattleVector2, DurationMillis}
import services.battle.microservices.actors.objects.player.{BattlePlayerLifeState, BattlePlayerState, HitPoints, KillCount, Score}
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileImpactRules {
  /** 中文名：应用投射物impact（applyProjectileImpact）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def applyProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState],
    retainedProjectileTerminalCount: BattleHistoryCount,
    retainedBattleEventCount: BattleHistoryCount
  ): BattleAggregateState =
    if projectile.projectileKind == ProjectileKind.Rocket && projectile.splashRadius.value > 0.0 then
      applyRocketProjectileImpact(state, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, directTarget, retainedProjectileTerminalCount, retainedBattleEventCount)
    else
      val (damagedState, report) = directTarget match {
        case Some(target) => damageProjectileTarget(state, projectile, target, retainedBattleEventCount)
        case None         => state -> None
      }
      appendProjectileTerminal(
        damagedState,
        terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, report),
        retainedProjectileTerminalCount
      )

  private def applyRocketProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState],
    retainedProjectileTerminalCount: BattleHistoryCount,
    retainedBattleEventCount: BattleHistoryCount
  ): BattleAggregateState = {
    val splashTargets = state.players
      .filter(player =>
        player.alive &&
          player.heroId != projectile.ownerHeroId &&
          distanceBetween(player.position, terminalPosition) <= projectile.splashRadius.value + BattleArenaCatalog.PlayerCollisionRadius
      )
      .sortBy(player =>
        if directTarget.exists(_.playerId == player.playerId) then -1.0
        else distanceBetween(player.position, terminalPosition)
      )

    val (damagedState, reports) = splashTargets.foldLeft(state -> Vector.empty[ProjectileDamageReport]) {
      case ((currentState, currentReports), target) =>
        val currentTarget = currentState.players.find(_.playerId == target.playerId)
        currentTarget match {
          case Some(player) if player.alive =>
            val (nextState, report) = damageProjectileTarget(currentState, projectile, player, retainedBattleEventCount)
            nextState -> (currentReports ++ report)
          case _ =>
            currentState -> currentReports
        }
    }

    appendProjectileTerminal(
      damagedState,
      terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, reports.headOption),
      retainedProjectileTerminalCount
    )
  }

  private def damageProjectileTarget(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    target: BattlePlayerState,
    retainedBattleEventCount: BattleHistoryCount
  ): (BattleAggregateState, Option[ProjectileDamageReport]) =
    if !target.alive || target.heroId == projectile.ownerHeroId then state -> None
    else {
      val hpBefore = target.hp
      val hpAfterValue = math.max(0, target.hp.value - projectile.damage.value)
      val eliminated = hpAfterValue <= 0
      val damagedTarget =
        if eliminated then
          clearDeadPlayerRuntime(target.copy(
            hp = HitPoints(0),
            lifeState = BattlePlayerLifeState.eliminated(
              target.eliminatedAtMs.orElse(Some(state.elapsedMs)),
              DurationMillis(0L)
            )
          ))
        else target.copy(hp = HitPoints(hpAfterValue))

      val creditedOwner = state.players.find(_.heroId == projectile.ownerHeroId).map { owner =>
        if eliminated then owner.copy(score = Score(owner.score.value + 1), kills = KillCount(owner.kills.value + 1))
        else owner
      }

      val updatedPlayers = state.players.map { player =>
        if player.playerId == damagedTarget.playerId then damagedTarget
        else creditedOwner.filter(_.playerId == player.playerId).getOrElse(player)
      }
      val stateWithPlayers = state.copy(players = updatedPlayers)
      val stateWithEvents =
        if eliminated then
          creditedOwner match {
            case Some(owner) =>
              stateWithPlayers.copy(events = retainRecentEvents(stateWithPlayers.events :+ battleEvent(stateWithPlayers, BattleEventKind.Kill, owner, damagedTarget), retainedBattleEventCount))
            case None => stateWithPlayers
          }
        else stateWithPlayers

      stateWithEvents -> Some(ProjectileDamageReport(target.copy(hp = hpBefore), damagedTarget))
    }

  private def retainRecentEvents(
    events: Vector[services.battle.microservices.runtime.objects.event.BattleEventState],
    retainedCount: BattleHistoryCount
  ): Vector[services.battle.microservices.runtime.objects.event.BattleEventState] =
    events.takeRight(retainedCount.value)
}
