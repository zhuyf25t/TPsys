package services.battle.engine


import services.battle.objects.*
import services.battle.engine.BattleAggregateUpdateRules.*
import services.battle.engine.BattleGeometry.*
import services.battle.engine.BattleMotionRules.*
import services.battle.engine.BattleProjectileFactoryRules.*
import services.battle.engine.BattleWeaponRules.*

private[services] object BattleWeaponFireRules {
  /** 中文名：应用primary开火（applyPrimaryFire）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def applyPrimaryFire(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq
  ): BattleAggregateState =
    currentWeapon(shooter) match {
      case Some(weapon) if weapon.weaponKind == WeaponKind.Pistol && canFireMagazineWeapon(weapon) =>
        val definition = BattleWeaponCatalog.fireDefinition(WeaponKind.Pistol)
        val chargedWeapon = chargeMagazineWeapon(weapon, definition.cooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength)
        resolvePistolShot(replacePlayer(state, recoiledShooter), chargedShooter, commandSeq, definition.projectile)
      case Some(weapon) if weapon.weaponKind == WeaponKind.RocketLauncher && canFireMagazineWeapon(weapon) =>
        val definition = BattleWeaponCatalog.fireDefinition(WeaponKind.RocketLauncher)
        val chargedWeapon = chargeMagazineWeapon(weapon, definition.cooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectile = definition.projectile
          )
        )
      case Some(weapon) if weapon.weaponKind == WeaponKind.Gatling && canFireHeatWeapon(weapon) =>
        val definition = BattleWeaponCatalog.fireDefinition(WeaponKind.Gatling)
        val chargedWeapon = chargeHeatWeapon(weapon, definition)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectile = definition.projectile
          )
        )
      case Some(weapon) if weapon.weaponKind == WeaponKind.Shotgun && canFireMagazineWeapon(weapon) =>
        val definition = BattleWeaponCatalog.fireDefinition(WeaponKind.Shotgun)
        val chargedWeapon = chargeMagazineWeapon(weapon, definition.cooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectile = definition.projectile
          )
        )
      case Some(weapon) if shouldAutoReload(weapon) =>
        replacePlayer(state, updateCurrentWeapon(shooter, startMagazineReload(weapon)))
      case _ => state
    }

  /** 中文名：解析requestedreloads（resolveRequestedReloads）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def resolveRequestedReloads(state: BattleAggregateState): BattleAggregateState =
    state.players.foldLeft(state) { (currentState, snapshotPlayer) =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.reloadPressed =>
          currentWeapon(player) match {
            case Some(weapon) if canStartMagazineReload(weapon) =>
              replacePlayer(
                currentState,
                updateCurrentWeapon(player.copy(reloadPressed = false), startMagazineReload(weapon))
              )
            case _ =>
              replacePlayer(currentState, player.copy(reloadPressed = false))
          }
        case _ => currentState
      }
    }

  /** 中文名：runtime开火命令seq（runtimeFireCommandSeq）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def runtimeFireCommandSeq(state: BattleAggregateState, player: BattlePlayerState): ClientCommandSeq =
    ClientCommandSeq(-(((state.tick.value + 1L) * 1_000L) + player.seat.value.toLong + 1L))

  private def applyWeaponRecoil(
    player: BattlePlayerState,
    direction: BattleVector2,
    recoilStrength: BattleWeaponRecoilStrength
  ): BattlePlayerState = {
    val recoilDistance = math.min(24.0, math.max(0.0, recoilStrength.value) * 0.18)
    val recoilDirection = normalizeMovement(scale(direction, -1.0))
    if recoilDistance <= 0.0 || vectorLength(recoilDirection) <= 0.0001 then player
    else {
      val motion = findMotionDestination(
        position = player.position,
        direction = recoilDirection,
        distance = recoilDistance,
        radius = BattleArenaCatalog.PlayerCollisionRadius
      )
      player.copy(position = motion.destination)
    }
  }

  private def chargeHeatWeapon(
    weapon: BattleWeaponState,
    definition: BattleWeaponFireDefinition
  ): BattleWeaponState =
    definition.heat match {
      case None => weapon
      case Some(heatDefinition) =>
        val heatAfter = math.min(
          heatDefinition.maxHeat.value,
          weapon.heat + heatDefinition.heatPerShot.value
        )
        val overheated = heatAfter >= heatDefinition.maxHeat.value
        weapon.copy(
          fireCooldownMs = definition.cooldownMs,
          heat = heatAfter,
          thermalState =
            if overheated then BattleWeaponThermalState.overheated(heatDefinition.overheatLockMs)
            else weapon.thermalState
        )
    }
}
