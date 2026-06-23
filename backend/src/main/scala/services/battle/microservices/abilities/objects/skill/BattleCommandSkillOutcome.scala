package services.battle.microservices.abilities.objects.skill

final case class BattleCommandSkillOutcome(
  action: SkillKind,
  outcomeStatus: SkillOutcomeStatus,
  reason: Option[SkillOutcomeReason]
)
