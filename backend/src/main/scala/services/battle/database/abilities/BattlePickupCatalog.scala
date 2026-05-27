package services.battle.database.abilities

import services.battle.database.world.BattleArenaCatalog
import services.battle.objects.{BattlePickupDefinition, BattlePickupState, DurationMillis, HitPoints, Radius}

private[services] object BattlePickupCatalog {
  val ContactRadius: Radius = Radius(40.0)
  val RespawnDuration: DurationMillis = DurationMillis(10000L)
  val MedkitHeal: HitPoints = HitPoints(25)

  def InitialPickups: Vector[BattlePickupDefinition] =
    BattleArenaCatalog.PickupDefinitions

  def initialPickups: Vector[BattlePickupState] =
    InitialPickups.map(_.initialState)
}
