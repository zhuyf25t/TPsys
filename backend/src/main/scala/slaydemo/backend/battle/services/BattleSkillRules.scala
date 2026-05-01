package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{BattlePlayerSkillState, SkillKind, SkillOutcomeReason}

private[services] object BattleSkillRules {
  def availabilityFailure(
    skills: Vector[BattlePlayerSkillState],
    skillKind: SkillKind
  ): Option[SkillOutcomeReason] =
    skills.find(_.skillKind == skillKind) match {
      case None =>
        Some(SkillOutcomeReason.SkillNotOwned)
      case Some(skill) if skill.cooldownMs.value > 0 =>
        Some(SkillOutcomeReason.Cooldown)
      case Some(_) =>
        None
    }
}
