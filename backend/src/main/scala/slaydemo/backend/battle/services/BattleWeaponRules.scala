package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleWeaponRules {
  def currentWeapon(player: BattlePlayerState): Option[BattleWeaponState] =
    player.weapons.lift(player.currentWeaponIndex)

  def updateCurrentWeapon(player: BattlePlayerState, weapon: BattleWeaponState): BattlePlayerState =
    player.copy(
      weapons = player.weapons.zipWithIndex.map { case (existing, index) =>
        if index == player.currentWeaponIndex then weapon else existing
      },
      currentWeaponKind = weapon.weaponKind
    )

  def canFireMagazineWeapon(weapon: BattleWeaponState): Boolean =
    weapon.ammoInMagazine.value > 0 &&
      weapon.fireCooldownMs.value <= 0 &&
      weapon.reloadRemainingMs.value <= 0

  def canFireHeatWeapon(weapon: BattleWeaponState): Boolean =
    weapon.fireCooldownMs.value <= 0 &&
      weapon.reloadRemainingMs.value <= 0 &&
      weapon.thermalState == BattleWeaponThermalState.Ready

  def chargeMagazineWeapon(weapon: BattleWeaponState, cooldownMs: CooldownMillis): BattleWeaponState =
    val chargedWeapon = weapon.copy(
      ammoInMagazine = AmmoCount(weapon.ammoInMagazine.value - 1),
      fireCooldownMs = cooldownMs
    )
    if shouldAutoReload(chargedWeapon) then startMagazineReload(chargedWeapon)
    else chargedWeapon

  def shouldAutoReload(weapon: BattleWeaponState): Boolean =
    weapon.ammoInMagazine.value <= 0 && canStartMagazineReload(weapon)

  def canStartMagazineReload(weapon: BattleWeaponState): Boolean =
    !weaponUsesHeat(weapon.weaponKind) &&
      weapon.reloadRemainingMs.value <= 0 &&
      weapon.ammoInMagazine.value < weapon.magazineSize.value &&
      weapon.reserveAmmo.exists(_.value > 0) &&
      weaponReloadMs(weapon.weaponKind) > 0

  def startMagazineReload(weapon: BattleWeaponState): BattleWeaponState =
    weapon.copy(reloadRemainingMs = CooldownMillis(weaponReloadMs(weapon.weaponKind)))

  def finishReload(weapon: BattleWeaponState): BattleWeaponState =
    weapon.reserveAmmo match {
      case None =>
        weapon.copy(reloadRemainingMs = CooldownMillis(0))
      case Some(reserve) =>
        val needed = math.max(0, weapon.magazineSize.value - weapon.ammoInMagazine.value)
        val loaded = math.min(needed, reserve.value)
        weapon.copy(
          ammoInMagazine = AmmoCount(weapon.ammoInMagazine.value + loaded),
          reserveAmmo = Some(AmmoCount(reserve.value - loaded)),
          reloadRemainingMs = CooldownMillis(0)
        )
    }

  def createWeaponState(weaponKind: WeaponKind): BattleWeaponState = {
    val definition = BattleWeaponCatalog.inventoryDefinition(weaponKind)
    BattleWeaponState(
      weaponKind = definition.weaponKind,
      ammoInMagazine = AmmoCount(if usesHeatResource(definition) then 0 else definition.magazineSize),
      magazineSize = AmmoCount(definition.magazineSize),
      reserveAmmo = definition.reserveAmmo.map(AmmoCount.apply),
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = 0,
      thermalState = BattleWeaponThermalState.Ready
    )
  }

  def refillWeaponState(weapon: BattleWeaponState): BattleWeaponState = {
    val definition = BattleWeaponCatalog.inventoryDefinition(weapon.weaponKind)
    val reserve = weapon.reserveAmmo.map(ammo => AmmoCount(ammo.value + definition.pickupAmmo))
    weapon.copy(
      ammoInMagazine = AmmoCount(if usesHeatResource(definition) then 0 else definition.magazineSize),
      magazineSize = AmmoCount(definition.magazineSize),
      reserveAmmo = reserve,
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = 0,
      thermalState = BattleWeaponThermalState.Ready
    )
  }

  def equipOrRefillWeapon(player: BattlePlayerState, weaponKind: WeaponKind): BattlePlayerState = {
    val existingIndex = player.weapons.indexWhere(_.weaponKind == weaponKind)
    if existingIndex >= 0 then
      val nextWeapons = player.weapons.updated(existingIndex, refillWeaponState(player.weapons(existingIndex)))
      player.copy(weapons = nextWeapons)
    else
      player.copy(weapons = player.weapons :+ createWeaponState(weaponKind))
  }

  def applyWeaponSwitchRequest(
    player: BattlePlayerState,
    direction: BattleWeaponSwitchDirection,
    requestedIndex: Option[BattleWeaponSwitchIndex]
  ): BattlePlayerState = {
    val weapons = if player.weapons.nonEmpty then player.weapons else Vector(createWeaponState(WeaponKind.Pistol))
    val currentIndex = clampWeaponIndex(player.currentWeaponIndex, weapons.length)
    val switchDirection = BattleWeaponSwitchDirection.step(direction)
    val targetIndex = requestedIndex
      .map(_.value)
      .filter(index => index < weapons.length)
      .filter(_ != currentIndex)
      .orElse {
        if switchDirection == 0 || weapons.length <= 1 then None
        else Some((currentIndex + switchDirection + weapons.length) % weapons.length)
      }

    targetIndex match {
      case Some(index) if player.alive && weapons.length > 1 =>
        val cancelledReloadWeapon = weapons(currentIndex).copy(reloadRemainingMs = CooldownMillis(0))
        val nextWeapons = weapons.updated(currentIndex, cancelledReloadWeapon)
        player.copy(
          currentWeaponIndex = index,
          currentWeaponKind = nextWeapons(index).weaponKind,
          weapons = nextWeapons
        )
      case _ =>
        player.copy(
          currentWeaponIndex = currentIndex,
          currentWeaponKind = weapons(currentIndex).weaponKind,
          weapons = weapons
        )
    }
  }

  def clampWeaponIndex(index: Int, weaponCount: Int): Int =
    if weaponCount <= 0 then 0 else math.max(0, math.min(index, weaponCount - 1))

  def weaponUsesHeat(weaponKind: WeaponKind): Boolean =
    BattleWeaponCatalog.usesHeatResource(weaponKind)

  private def usesHeatResource(definition: BattleWeaponInventoryDefinition): Boolean =
    definition.usesHeatResource

  private def weaponReloadMs(weaponKind: WeaponKind): Int =
    BattleWeaponCatalog.reloadMs(weaponKind)
}
