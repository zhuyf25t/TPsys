package slaydemo.backend.battle.services.actors

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.{DurationMillis, Radius}

private[services] final case class BattleBotMoveSpeed(value: Double) extends AnyVal

private[services] object BattleBotCatalog {
  val MoveSpeed: BattleBotMoveSpeed = BattleBotMoveSpeed(108.0)
  val PreferredRange: Radius = Radius(260.0)
  val PreferredRangeAdvanceMargin: Radius = Radius(120.0)
  val PreferredRangeRetreatMargin: Radius = Radius(90.0)
  val BotFireRange: Radius = Radius(520.0)
  val HumanFireRange: Radius = Radius(360.0)
  val HumanOpeningFireDelay: DurationMillis = DurationMillis(15000L)
}
