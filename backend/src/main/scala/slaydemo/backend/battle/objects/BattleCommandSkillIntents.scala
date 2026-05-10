package slaydemo.backend.battle.objects

final case class BattleCommandSkillIntents(values: Vector[SkillKind]) {
  def nonEmpty: Boolean =
    values.nonEmpty
}

object BattleCommandSkillIntents {
  val empty: BattleCommandSkillIntents =
    BattleCommandSkillIntents(Vector.empty)

  def fromLegacyFlags(
    castDash: Boolean,
    castBlink: Boolean,
    castFreeze: Boolean
  ): BattleCommandSkillIntents =
    BattleCommandSkillIntents(
      Vector(
        Option.when(castBlink)(SkillKind.Blink),
        Option.when(castDash)(SkillKind.Dash),
        Option.when(castFreeze)(SkillKind.Freeze)
      ).flatten
    )
}
