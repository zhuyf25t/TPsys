package services.battle.microservices.abilities.api

import io.circe.Encoder
import services.battle.microservices.abilities.objects.skill.{
  BattleCommandSkillOutcome,
  SkillKind,
  SkillOutcomeReason,
  SkillOutcomeStatus
}

object BattleSkillOutcomeAPIEncoding {
  given Encoder[BattleCommandSkillOutcome] =
    Encoder
      .forProduct3("action", "status", "reason")((outcome: BattleCommandSkillOutcome) =>
        (
          SkillKind.wireValue(outcome.action),
          SkillOutcomeStatus.wireValue(outcome.outcomeStatus),
          outcome.reason.map(SkillOutcomeReason.wireValue)
        )
      )
      .mapJson(_.dropNullValues)
}
