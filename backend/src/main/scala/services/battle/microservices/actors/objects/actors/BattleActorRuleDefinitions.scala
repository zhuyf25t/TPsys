package services.battle.microservices.actors.objects.actors

import services.battle.objects.core.{DurationMillis, Radius}

private[services] final case class BattleBotMoveSpeed(value: Double) extends AnyVal

private[services] final case class BattleBotRuleConfig(
  moveSpeed: BattleBotMoveSpeed,
  preferredRange: Radius,
  preferredRangeAdvanceMargin: Radius,
  preferredRangeRetreatMargin: Radius,
  botFireRange: Radius,
  humanFireRange: Radius,
  openingFireDelay: DurationMillis,
  firePulseInterval: DurationMillis,
  firePulseWindow: DurationMillis,
  movementProbeDistance: Radius,
  coverProbeDistance: Radius,
  pickupSeekRange: Radius,
  aimLeadDistance: Radius,
  aimErrorRadius: Radius,
  lowHealthRatio: Double,
  pickupHealthRatio: Double,
  tacticalReloadRatio: Double
)
