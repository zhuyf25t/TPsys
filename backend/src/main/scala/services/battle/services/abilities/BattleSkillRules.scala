package services.battle.services.abilities

import services.battle.services.*

import services.battle.objects.{BattlePlayerSkillState, SkillKind, SkillOutcomeReason}

private[services] object BattleSkillRules {
  /** 中文名：availabilityfailure（availabilityFailure）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互。 */
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
