package slaydemo.backend.battle.services.abilities

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*

private[services] final case class BattlePickupDefinition(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2
) {
  /** 中文名：initial状态（initialState）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互。 */
  def initialState: BattlePickupState =
    BattlePickupState(
      pickupId = pickupId,
      pickupKind = pickupKind,
      weaponKind = weaponKind,
      position = position,
      pickupAvailability = BattlePickupAvailability.Available
    )
}

private[services] object BattlePickupCatalog {
  val ContactRadius: Radius = Radius(40.0)
  val RespawnDuration: DurationMillis = DurationMillis(10000L)
  val MedkitHeal: HitPoints = HitPoints(25)

  val InitialPickups: Vector[BattlePickupDefinition] =
    Vector(
      BattlePickupDefinition(
        pickupId = PickupId("pickup-medkit-1"),
        pickupKind = PickupKind.Medkit,
        weaponKind = None,
        position = BattleVector2(960.0, 608.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-medkit-2"),
        pickupKind = PickupKind.Medkit,
        weaponKind = None,
        position = BattleVector2(1600.0, 992.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-rocket-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.RocketLauncher),
        position = BattleVector2(1280.0, 256.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-gatling-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Gatling),
        position = BattleVector2(704.0, 800.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-shotgun-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Shotgun),
        position = BattleVector2(1856.0, 800.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-rocket-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.RocketLauncher),
        position = BattleVector2(1280.0, 1344.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-gatling-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Gatling),
        position = BattleVector2(448.0, 800.0)
      ),
      BattlePickupDefinition(
        pickupId = PickupId("pickup-shotgun-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Shotgun),
        position = BattleVector2(2112.0, 800.0)
      )
    )

  /** 中文名：initialpickups（initialPickups）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互。 */
  def initialPickups: Vector[BattlePickupState] =
    InitialPickups.map(_.initialState)
}
