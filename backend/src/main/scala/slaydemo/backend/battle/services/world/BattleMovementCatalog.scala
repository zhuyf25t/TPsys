package slaydemo.backend.battle.services.world

import slaydemo.backend.battle.services.*

private[services] final case class BattleMovementSpeed(value: Double) extends AnyVal
private[services] final case class BattleStaminaRatePerSecond(value: Double) extends AnyVal
private[services] final case class BattleSlowFactor(value: Double) extends AnyVal

private[services] object BattleMovementCatalog {
  val WalkSpeed: BattleMovementSpeed = BattleMovementSpeed(255.0)
  val SprintSpeed: BattleMovementSpeed = BattleMovementSpeed(446.25)
  val StaminaDrainPerSecond: BattleStaminaRatePerSecond = BattleStaminaRatePerSecond(38.0)
  val StaminaRecoverPerSecond: BattleStaminaRatePerSecond = BattleStaminaRatePerSecond(24.0)
  val SlowFieldMovementFactor: BattleSlowFactor = BattleSlowFactor(0.5)
  val SlowFieldProjectileFactor: BattleSlowFactor = BattleSlowFactor(0.5)
}
