package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleBotRules.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleMotionRules.*
import slaydemo.backend.battle.services.BattlePlayerLifecycleRules.*
import slaydemo.backend.battle.services.BattleTimeRules.*
import slaydemo.backend.battle.services.BattleWeaponRules.*

private[services] object BattlePlayerRuntimeRules {
  def advancePlayers(state: BattleAggregateState, deltaMs: Long): BattleAggregateState = {
    val nextElapsed = state.elapsedMs.value
    val previousElapsed = math.max(0L, nextElapsed - deltaMs)
    val advancedPlayers = state.players.map { player =>
      val withTimers = advancePlayerTimers(player, deltaMs, previousElapsed, nextElapsed)
      val controlledPlayer =
        if withTimers.alive && withTimers.isBot then applyBotControl(withTimers, state)
        else withTimers
      if controlledPlayer.alive then movePlayer(controlledPlayer, deltaMs, state.slowFields, previousElapsed, nextElapsed)
      else
        clearDeadPlayerRuntime(controlledPlayer)
    }

    state.copy(players = advancedPlayers)
  }

  def advancePlayerTimers(
    player: BattlePlayerState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): BattlePlayerState = {
    val withSkills = player.copy(skills = player.skills.map { skill =>
      skill.copy(
        cooldownMs = CooldownMillis(decrementInt(skill.cooldownMs.value, deltaMs)),
        activeMs = DurationMillis(decrementLong(skill.activeMs.value, deltaMs))
      )
    })

    val timedWeapons = withSkills.weapons.zipWithIndex.map { case (weapon, index) =>
      val heatAdvanced = advanceWeaponHeat(weapon, deltaMs, previousElapsed, nextElapsed)
      if index == withSkills.currentWeaponIndex then
        val timerWeapon = heatAdvanced.copy(
          fireCooldownMs = CooldownMillis(decrementInt(heatAdvanced.fireCooldownMs.value, deltaMs))
        )
        if timerWeapon.reloadRemainingMs.value <= 0 then timerWeapon
        else {
          val remaining = decrementInt(timerWeapon.reloadRemainingMs.value, deltaMs)
          if remaining > 0 then timerWeapon.copy(reloadRemainingMs = CooldownMillis(remaining))
          else finishReload(timerWeapon)
        }
      else heatAdvanced
    }

    if timedWeapons.isEmpty then withSkills
    else {
      val currentIndex = clampWeaponIndex(withSkills.currentWeaponIndex, timedWeapons.length)
      withSkills.copy(
        currentWeaponIndex = currentIndex,
        currentWeaponKind = timedWeapons(currentIndex).weaponKind,
        weapons = timedWeapons
      )
    }
  }

  def movePlayer(
    player: BattlePlayerState,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState],
    previousElapsed: Long,
    nextElapsed: Long
  ): BattlePlayerState = {
    val hasMovement = vectorLength(player.movement) > 0.0 && deltaMs > 0L
    val canSprint = player.sprint && hasMovement && player.stamina.value > 0.0
    val nextStamina = advanceStamina(player, canSprint, deltaMs, previousElapsed, nextElapsed)
    val withStamina = player.copy(stamina = nextStamina, sprint = canSprint)

    if !hasMovement then withStamina
    else {
      val baseSpeed =
        if player.isBot then BattleBotCatalog.MoveSpeed.value
        else if canSprint then BattleMovementCatalog.SprintSpeed.value
        else BattleMovementCatalog.WalkSpeed.value
      val slowFactor =
        if slowFields.exists(field => distanceBetween(player.position, field.position) <= field.radius.value) then
          BattleMovementCatalog.SlowFieldMovementFactor.value
        else 1.0
      val distance = baseSpeed * slowFactor * deltaMs.toDouble / 1000.0
      val motion = findMotionDestination(
        position = player.position,
        direction = player.movement,
        distance = distance,
        radius = BattleArenaCatalog.PlayerCollisionRadius
      )
      withStamina.copy(position = motion.destination)
    }
  }

  private def advanceWeaponHeat(
    weapon: BattleWeaponState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): BattleWeaponState = {
    BattleWeaponCatalog.heatDefinition(weapon.weaponKind) match {
      case None =>
        weapon.copy(heat = 0, thermalState = BattleWeaponThermalState.Ready)
      case Some(heatDefinition) =>
        val timerWeapon = weapon.copy(
          thermalState = BattleWeaponThermalState.overheated(
            CooldownMillis(decrementInt(weapon.overheatRemainingMs.value, deltaMs))
          )
        )
        val heatDelta = elapsedRateDelta(heatDefinition.coolRatePerSecond.value, previousElapsed, nextElapsed)
        timerWeapon.copy(heat = math.max(0, timerWeapon.heat - heatDelta))
    }
  }

  private def advanceStamina(
    player: BattlePlayerState,
    sprinting: Boolean,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): Stamina = {
    if deltaMs <= 0L then player.stamina
    else {
      val delta =
        if sprinting then -BattleMovementCatalog.StaminaDrainPerSecond.value
        else BattleMovementCatalog.StaminaRecoverPerSecond.value
      val staminaDelta = elapsedRateDeltaDouble(math.abs(delta), previousElapsed, nextElapsed)
      val signedDelta = if delta < 0 then -staminaDelta else staminaDelta
      Stamina(math.max(0, math.min(player.maxStamina.value, player.stamina.value + signedDelta)))
    }
  }
}
