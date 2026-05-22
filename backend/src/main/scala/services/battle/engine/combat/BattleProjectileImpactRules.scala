package services.battle.engine


import services.battle.objects.*
import services.battle.engine.BattleEventFactory.*
import services.battle.engine.BattleGeometry.*
import services.battle.engine.BattlePlayerLifecycleRules.*
import services.battle.engine.BattleProjectileTerminalRules.*
import services.battle.engine.BattleRetentionRules.*

private[services] object BattleProjectileImpactRules {
  /** 中文名：应用投射物impact（applyProjectileImpact）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def applyProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState]
  ): BattleAggregateState =
    if projectile.projectileKind == ProjectileKind.Rocket && projectile.splashRadius.value > 0.0 then
      applyRocketProjectileImpact(state, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, directTarget)
    else
      val (damagedState, report) = directTarget match {
        case Some(target) => damageProjectileTarget(state, projectile, target)
        case None         => state -> None
      }
      appendProjectileTerminal(
        damagedState,
        terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, report)
      )

  private def applyRocketProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState]
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
            val (nextState, report) = damageProjectileTarget(currentState, projectile, player)
            nextState -> (currentReports ++ report)
          case _ =>
            currentState -> currentReports
        }
    }

    appendProjectileTerminal(
      damagedState,
      terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, reports.headOption)
    )
  }

  private def damageProjectileTarget(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    target: BattlePlayerState
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
        if eliminated then owner.copy(score = Score(owner.score.value + 1), kills = owner.kills + 1)
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
              stateWithPlayers.copy(events = retainRecentEvents(stateWithPlayers.events :+ battleEvent(stateWithPlayers, BattleEventKind.Kill, owner, damagedTarget)))
            case None => stateWithPlayers
          }
        else stateWithPlayers

      stateWithEvents -> Some(ProjectileDamageReport(target.copy(hp = hpBefore), damagedTarget))
    }
}
