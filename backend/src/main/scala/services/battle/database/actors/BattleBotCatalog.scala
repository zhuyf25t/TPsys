package services.battle.database.actors

import services.battle.objects.core.{DurationMillis, Radius}

private[services] final case class BattleBotMoveSpeed(value: Double) extends AnyVal

private[services] object BattleBotCatalog {
  val MoveSpeed: BattleBotMoveSpeed = BattleBotMoveSpeed(108.0)
  val PreferredRange: Radius = Radius(260.0)
  val PreferredRangeAdvanceMargin: Radius = Radius(120.0)
  val PreferredRangeRetreatMargin: Radius = Radius(90.0)
  val BotFireRange: Radius = Radius(520.0)
  val HumanFireRange: Radius = Radius(360.0)
  val OpeningFireDelay: DurationMillis = DurationMillis(5000L)
  val FirePulseInterval: DurationMillis = DurationMillis(520L)
  val FirePulseWindow: DurationMillis = DurationMillis(120L)
  val MovementProbeDistance: Radius = Radius(96.0)
  val CoverProbeDistance: Radius = Radius(180.0)
  val PickupSeekRange: Radius = Radius(720.0)
  val AimLeadDistance: Radius = Radius(42.0)
  val AimErrorRadius: Radius = Radius(8.0)
  val LowHealthRatio: Double = 0.45
  val PickupHealthRatio: Double = 0.7
  val TacticalReloadRatio: Double = 0.35
}
