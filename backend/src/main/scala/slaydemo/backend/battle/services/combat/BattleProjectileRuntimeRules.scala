package slaydemo.backend.battle.services.combat

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.world.BattleGeometry.*
import slaydemo.backend.battle.services.combat.BattleProjectileImpactRules.*
import slaydemo.backend.battle.services.combat.BattleProjectileMotionRules.*
import slaydemo.backend.battle.services.combat.BattleProjectileTargetingRules.*
import slaydemo.backend.battle.services.runtime.BattleTimeRules.*

private[services] object BattleProjectileRuntimeRules {
  private final case class ProjectileAdvance(
    state: BattleAggregateState,
    activeProjectiles: Vector[BattleProjectileState]
  )

  /** 中文名：推进projectiles（advanceProjectiles）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def advanceProjectiles(state: BattleAggregateState, deltaMs: Long): BattleAggregateState = {
    val advanced = state.projectiles.foldLeft(ProjectileAdvance(state, Vector.empty)) { (current, projectile) =>
      val travelMs = math.min(math.max(0L, deltaMs), math.max(0L, projectile.ttlMs.value))
      val speedFactor =
        if state.slowFields.exists(field => distanceBetween(projectile.position, field.position) <= field.radius.value) then
          BattleMovementCatalog.SlowFieldProjectileFactor.value
        else 1.0
      val motion = resolveProjectileMotion(projectile, speedFactor, travelMs)
      val nextTtl = decrementLong(projectile.ttlMs.value, travelMs)
      val playerHit = findProjectilePlayerHit(current.state.players, projectile, motion.destination)
      val reason = playerHit match {
        case Some(_) => Some(ProjectileTerminalReason.Hit)
        case None =>
          if nextTtl <= 0L then Some(ProjectileTerminalReason.Expired)
          else motion.terminalReason
      }

      (reason, playerHit) match {
        case (Some(terminalReason), Some(hit)) =>
          current.copy(
            state = applyProjectileImpact(
              current.state,
              projectile,
              terminalReason,
              hit.position,
              motion.segmentEnd,
              nextTtl,
              Some(hit.player)
            )
          )
        case (Some(terminalReason), None) =>
          current.copy(
            state = applyProjectileImpact(
              current.state,
              projectile,
              terminalReason,
              motion.destination,
              motion.segmentEnd,
              nextTtl,
              None
            )
          )
        case (None, _) =>
          current.copy(
            activeProjectiles = current.activeProjectiles :+ projectile.copy(
              position = motion.destination,
              ttlMs = DurationMillis(nextTtl)
            )
          )
      }
    }

    advanced.state.copy(projectiles = advanced.activeProjectiles)
  }
}
