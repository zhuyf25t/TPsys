package slaydemo.backend.battle.objects

enum BattleSurvivalOutcome {
  case Survived
  case Eliminated
}

object BattleSurvivalOutcome {
  def fromAliveAtEnd(aliveAtEnd: Boolean): BattleSurvivalOutcome =
    if aliveAtEnd then BattleSurvivalOutcome.Survived else BattleSurvivalOutcome.Eliminated

  def aliveAtEnd(value: BattleSurvivalOutcome): Boolean =
    value == BattleSurvivalOutcome.Survived
}
