package services.battle.microservices.abilities.services

import cats.effect.IO

import services.battle.microservices.abilities.objects.skill.{SkillKind, SkillOutcomeReason}
import services.battle.microservices.actors.objects.player.BattlePlayerSkillState

private[battle] object BattleSkillRules {
  /** 中文名：availabilityfailure（availabilityFailure）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互�?*/
  def availabilityFailure(
    skills: Vector[BattlePlayerSkillState],
    skillKind: SkillKind
  ): IO[Option[SkillOutcomeReason]] =
    IO.pure(skills.find(_.skillKind == skillKind) match {
      case None =>
        Some(SkillOutcomeReason.SkillNotOwned)
      case Some(skill) if skill.cooldownMs.value > 0 =>
        Some(SkillOutcomeReason.Cooldown)
      case Some(_) =>
        None
    })
}
