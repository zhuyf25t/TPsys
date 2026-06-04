package services.battle.microservices.combat.services

import cats.effect.IO

import services.battle.microservices.combat.objects.combat.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.objects.core.CooldownMillis
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.weapon.{
  AmmoCount,
  BattleWeaponHeat,
  BattleWeaponState,
  BattleWeaponSwitchDirection,
  BattleWeaponSwitchIndex,
  BattleWeaponThermalState
}

private[battle] object BattleWeaponRules {
  def currentWeapon(player: BattlePlayerState): IO[Option[BattleWeaponState]] =
    IO.pure(player.weapons.lift(player.currentWeaponIndex))

  def fireDefinition(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[BattleWeaponFireDefinition] =
    battleRules.fireDefinition(weaponKind)

  def inventoryDefinition(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[BattleWeaponInventoryDefinition] =
    battleRules.inventoryDefinition(weaponKind)

  def heatDefinition(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[Option[BattleWeaponHeatDefinition]] =
    battleRules.heatDefinition(weaponKind)

  def updateCurrentWeapon(player: BattlePlayerState, weapon: BattleWeaponState): IO[BattlePlayerState] =
    IO.pure(player.copy(
      weapons = player.weapons.zipWithIndex.map { case (existing, index) =>
        if index == player.currentWeaponIndex then weapon else existing
      },
      currentWeaponKind = weapon.weaponKind
    ))

  def canFireMagazineWeapon(weapon: BattleWeaponState): IO[Boolean] =
    IO.pure(
      weapon.ammoInMagazine.value > 0 &&
        weapon.fireCooldownMs.value <= 0 &&
        weapon.reloadRemainingMs.value <= 0
    )

  def canFireHeatWeapon(weapon: BattleWeaponState): IO[Boolean] =
    IO.pure(
      weapon.fireCooldownMs.value <= 0 &&
        weapon.reloadRemainingMs.value <= 0 &&
        weapon.thermalState == BattleWeaponThermalState.Ready
    )

  def chargeMagazineWeapon(
    weapon: BattleWeaponState,
    cooldownMs: CooldownMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleWeaponState] =
    val chargedWeapon = weapon.copy(
      ammoInMagazine = AmmoCount(weapon.ammoInMagazine.value - 1),
      fireCooldownMs = cooldownMs
    )
    shouldAutoReload(chargedWeapon, battleRules).flatMap {
      case true  => startMagazineReload(chargedWeapon, battleRules)
      case false => IO.pure(chargedWeapon)
    }

  def shouldAutoReload(weapon: BattleWeaponState, battleRules: BattleDynamicRuleBook): IO[Boolean] =
    canStartMagazineReload(weapon, battleRules).map(weapon.ammoInMagazine.value <= 0 && _)

  def canStartMagazineReload(weapon: BattleWeaponState, battleRules: BattleDynamicRuleBook): IO[Boolean] =
    for
      usesHeat <- weaponUsesHeat(weapon.weaponKind, battleRules)
      reloadMs <- weaponReloadMs(weapon.weaponKind, battleRules)
    yield !usesHeat &&
      weapon.reloadRemainingMs.value <= 0 &&
      weapon.ammoInMagazine.value < weapon.magazineSize.value &&
      weapon.reserveAmmo.exists(_.value > 0) &&
      reloadMs > 0

  def startMagazineReload(weapon: BattleWeaponState, battleRules: BattleDynamicRuleBook): IO[BattleWeaponState] =
    weaponReloadMs(weapon.weaponKind, battleRules).map(reloadMs => weapon.copy(reloadRemainingMs = CooldownMillis(reloadMs)))

  def finishReload(weapon: BattleWeaponState): IO[BattleWeaponState] =
    IO.pure {
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
    }

  def createWeaponState(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[BattleWeaponState] =
    inventoryDefinition(weaponKind, battleRules).flatMap { definition =>
      usesHeatResource(definition).map { usesHeat =>
        BattleWeaponState(
          weaponKind = definition.weaponKind,
          ammoInMagazine = AmmoCount(if usesHeat then 0 else definition.magazineSize),
          magazineSize = AmmoCount(definition.magazineSize),
          reserveAmmo = definition.reserveAmmo.map(AmmoCount.apply),
          fireCooldownMs = CooldownMillis(0),
          reloadRemainingMs = CooldownMillis(0),
          heat = BattleWeaponHeat(0),
          thermalState = BattleWeaponThermalState.Ready
        )
      }
    }

  def refillWeaponState(weapon: BattleWeaponState, battleRules: BattleDynamicRuleBook): IO[BattleWeaponState] =
    inventoryDefinition(weapon.weaponKind, battleRules).flatMap { definition =>
      usesHeatResource(definition).map { usesHeat =>
        val reserve = weapon.reserveAmmo.map(ammo => AmmoCount(ammo.value + definition.pickupAmmo))
        weapon.copy(
          ammoInMagazine = AmmoCount(if usesHeat then 0 else definition.magazineSize),
          magazineSize = AmmoCount(definition.magazineSize),
          reserveAmmo = reserve,
          fireCooldownMs = CooldownMillis(0),
          reloadRemainingMs = CooldownMillis(0),
          heat = BattleWeaponHeat(0),
          thermalState = BattleWeaponThermalState.Ready
        )
      }
    }

  def equipOrRefillWeapon(
    player: BattlePlayerState,
    weaponKind: WeaponKind,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    val existingIndex = player.weapons.indexWhere(_.weaponKind == weaponKind)
    if existingIndex >= 0 then
      refillWeaponState(player.weapons(existingIndex), battleRules).map { weapon =>
        val nextWeapons = player.weapons.updated(existingIndex, weapon)
        player.copy(weapons = nextWeapons)
      }
    else
      createWeaponState(weaponKind, battleRules).map(weapon => player.copy(weapons = player.weapons :+ weapon))
  }

  def applyWeaponSwitchRequest(
    player: BattlePlayerState,
    direction: BattleWeaponSwitchDirection,
    requestedIndex: Option[BattleWeaponSwitchIndex],
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] =
    val weaponsIO =
      if player.weapons.nonEmpty then IO.pure(player.weapons)
      else createWeaponState(WeaponKind.Pistol, battleRules).map(Vector(_))

    weaponsIO.flatMap { weapons =>
      for
        currentIndex <- clampWeaponIndex(player.currentWeaponIndex, weapons.length)
        switchDirection = BattleWeaponSwitchDirection.step(direction)
        targetIndex = requestedIndex
          .map(_.value)
          .filter(index => index < weapons.length)
          .filter(_ != currentIndex)
          .orElse {
            if switchDirection == 0 || weapons.length <= 1 then None
            else Some((currentIndex + switchDirection + weapons.length) % weapons.length)
          }
      yield targetIndex match {
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

  def clampWeaponIndex(index: Int, weaponCount: Int): IO[Int] =
    IO.pure(if weaponCount <= 0 then 0 else math.max(0, math.min(index, weaponCount - 1)))

  def weaponUsesHeat(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[Boolean] =
    inventoryDefinition(weaponKind, battleRules).flatMap(usesHeatResource)

  private def usesHeatResource(definition: BattleWeaponInventoryDefinition): IO[Boolean] =
    IO.pure(definition.usesHeatResource)

  private def weaponReloadMs(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[Int] =
    inventoryDefinition(weaponKind, battleRules).map(_.reloadMs)
}
