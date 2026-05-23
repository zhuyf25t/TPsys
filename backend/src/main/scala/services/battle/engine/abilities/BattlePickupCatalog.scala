package services.battle.engine


import services.battle.objects.*

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

  def InitialPickups: Vector[BattlePickupDefinition] =
    BattleArenaCatalog.PickupDefinitions

  /** 中文名：initialpickups（initialPickups）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互。 */
  def initialPickups: Vector[BattlePickupState] =
    InitialPickups.map(_.initialState)
}
