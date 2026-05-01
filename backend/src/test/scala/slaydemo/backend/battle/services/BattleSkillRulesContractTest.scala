package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{
  BattlePlayerSkillState,
  CooldownMillis,
  DurationMillis,
  SkillKind,
  SkillOutcomeReason
}

object BattleSkillRulesContractTest {
  def main(args: Array[String]): Unit = {
    unavailableSkillIsNotOwned()
    coolingSkillIsCooldown()
    readySkillHasNoFailure()

    println("Battle skill rules contract checks passed")
  }

  private def unavailableSkillIsNotOwned(): Unit =
    assertEquals(
      "missing skill availability",
      BattleSkillRules.availabilityFailure(Vector.empty, SkillKind.Blink),
      Some(SkillOutcomeReason.SkillNotOwned)
    )

  private def coolingSkillIsCooldown(): Unit =
    assertEquals(
      "cooling skill availability",
      BattleSkillRules.availabilityFailure(
        Vector(BattlePlayerSkillState(SkillKind.Dash, CooldownMillis(1200), DurationMillis(0L))),
        SkillKind.Dash
      ),
      Some(SkillOutcomeReason.Cooldown)
    )

  private def readySkillHasNoFailure(): Unit =
    assertEquals(
      "ready skill availability",
      BattleSkillRules.availabilityFailure(
        Vector(BattlePlayerSkillState(SkillKind.Freeze, CooldownMillis(0), DurationMillis(0L))),
        SkillKind.Freeze
      ),
      None
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
