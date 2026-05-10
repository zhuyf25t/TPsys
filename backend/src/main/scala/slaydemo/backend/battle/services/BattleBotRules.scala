package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleInitialLayout.*
import slaydemo.backend.battle.services.BattleInputRules.*
import slaydemo.backend.battle.services.BattleMotionRules.*
import slaydemo.backend.battle.services.BattleWeaponRules.*

private[services] object BattleBotRules {
  def applyBotControl(player: BattlePlayerState, state: BattleAggregateState): BattlePlayerState = {
    val aliveOpponents = state.players.filter(candidate => candidate.playerId != player.playerId && candidate.alive)
    val preferredTargets = aliveOpponents.filterNot(_.isBot)
    val targetPool = if preferredTargets.nonEmpty then preferredTargets else aliveOpponents

    targetPool.minByOption(candidate => distanceBetween(player.position, candidate.position)) match {
      case Some(target) =>
        val toTarget = subtract(target.position, player.position)
        val distance = vectorLength(toTarget)
        val aim = normalizeAim(player.aim, toTarget)
        val orbitDirection =
          if (state.tick.value + player.seat.value.toLong) % 2L == 0L then 1.0
          else -1.0
        val orbit = perpendicular(aim, orbitDirection)
        val radial =
          if distance > BattleBotCatalog.PreferredRange.value + BattleBotCatalog.PreferredRangeAdvanceMargin.value then aim
          else if distance < BattleBotCatalog.PreferredRange.value - BattleBotCatalog.PreferredRangeRetreatMargin.value then scale(aim, -1.0)
          else BattleArenaCatalog.ZeroVector
        val movement = normalizeMovement(add(scale(radial, 0.86), scale(orbit, 0.52)))
        val resolvedMovement =
          if vectorLength(movement) <= 0.0001 then orbit
          else movement

        player.copy(
          aim = aim,
          facing = FacingRadians(math.atan2(aim.y, aim.x)),
          movement = resolvedMovement,
          sprint = false,
          primaryHeld = distance <= botFireRangeForTarget(target) && canBotFireAtTarget(target, state),
          reloadPressed = shouldBotReload(player)
        )

      case None =>
        val spawnAnchor = spawnPointFor(SpawnPointIndex(player.seat.value))
        val patrolAngle = (state.tick.value + player.seat.value.toLong * 11L).toDouble * 0.18
        val patrolTarget = BattleVector2(
          clampDouble(spawnAnchor.x + math.cos(patrolAngle) * 140.0, 0.0, BattleArenaCatalog.WorldSize.x),
          clampDouble(spawnAnchor.y + math.sin(patrolAngle) * 110.0, 0.0, BattleArenaCatalog.WorldSize.y)
        )
        val movement = normalizeMovement(subtract(patrolTarget, player.position))
        val aim = normalizeAim(player.aim, movement)

        player.copy(
          aim = aim,
          facing = FacingRadians(math.atan2(aim.y, aim.x)),
          movement = movement,
          sprint = false,
          primaryHeld = false,
          reloadPressed = shouldBotReload(player)
        )
    }
  }

  private def shouldBotReload(player: BattlePlayerState): Boolean =
    player.isBot && currentWeapon(player).exists(weapon =>
      weapon.ammoInMagazine.value <= 0 && canStartMagazineReload(weapon)
    )

  private def canBotFireAtTarget(target: BattlePlayerState, state: BattleAggregateState): Boolean =
    target.isBot || state.elapsedMs.value >= BattleBotCatalog.HumanOpeningFireDelay.value

  private def botFireRangeForTarget(target: BattlePlayerState): Double =
    if target.isBot then BattleBotCatalog.BotFireRange.value
    else BattleBotCatalog.HumanFireRange.value
}
