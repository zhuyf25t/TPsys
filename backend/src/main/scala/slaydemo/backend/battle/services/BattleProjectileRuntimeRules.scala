package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleProjectileImpactRules.*
import slaydemo.backend.battle.services.BattleProjectileMotionRules.*
import slaydemo.backend.battle.services.BattleProjectileTargetingRules.*
import slaydemo.backend.battle.services.BattleTimeRules.*

private[services] object BattleProjectileRuntimeRules {
  private final case class ProjectileAdvance(
    state: BattleAggregateState,
    activeProjectiles: Vector[BattleProjectileState]
  )

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

      reason match {
        case Some(terminalReason) if playerHit.nonEmpty =>
          val hit = playerHit.get
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
        case Some(terminalReason) =>
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
        case None =>
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
