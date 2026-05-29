package services.battle.microservices.combat.services

import services.battle.microservices.runtime.objects.runtime.BattleHistoryCount
import services.battle.microservices.combat.objects.projectile.ProjectileTerminalReason
import services.battle.objects.core.{BattleAggregateState, BattleVector2, DurationMillis, PlayerId}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.projectile.{BattleProjectileState, BattleProjectileTerminalState}

private[battle] object BattleProjectileTerminalRules {
  final case class ProjectileDamageReport(
    targetBefore: BattlePlayerState,
    targetAfter: BattlePlayerState
  )

  /** 中文名：append投射物终止（appendProjectileTerminal）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def appendProjectileTerminal(
    state: BattleAggregateState,
    terminal: BattleProjectileTerminalState,
    retainedProjectileTerminalCount: BattleHistoryCount
  ): BattleAggregateState =
    state.copy(projectileTerminals = retainRecentProjectileTerminals(state.projectileTerminals :+ terminal, retainedProjectileTerminalCount))

  /** 中文名：终止for投射物（terminalForProjectile）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def terminalForProjectile(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    damageReport: Option[ProjectileDamageReport] = None
  ): BattleProjectileTerminalState = {
    val owner = state.players.find(_.heroId == projectile.ownerHeroId)
    BattleProjectileTerminalState(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      ownerPlayerId = owner.map(_.playerId).getOrElse(PlayerId(projectile.ownerHeroId.value)),
      ownerHeroId = projectile.ownerHeroId,
      reason = reason,
      start = projectile.position,
      end = segmentEnd,
      terminalPosition = terminalPosition,
      ttlBefore = projectile.ttlMs,
      ttlAfter = DurationMillis(math.max(0L, ttlAfterValue)),
      elapsedMs = state.elapsedMs,
      targetPlayerId = damageReport.map(_.targetAfter.playerId),
      targetHeroId = damageReport.map(_.targetAfter.heroId),
      hpBefore = damageReport.map(_.targetBefore.hp),
      hpAfter = damageReport.map(_.targetAfter.hp),
      damage = damageReport.map(_ => projectile.damage)
    )
  }

  private def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState],
    retainedCount: BattleHistoryCount
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(retainedCount.value)
}
