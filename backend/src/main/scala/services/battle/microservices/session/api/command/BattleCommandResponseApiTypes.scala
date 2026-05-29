package services.battle.microservices.session.api.command

import io.circe.Encoder
import services.battle.microservices.abilities.objects.skill.{SkillKind, SkillOutcomeReason, SkillOutcomeStatus}
import services.battle.microservices.session.objects.command.{
  BattleCommandAccepted,
  BattleCommandReason,
  BattleCommandSkillOutcome,
  BattleCommandStatus
}

object BattleCommandAcceptedResponse {
  private given Encoder[BattleCommandSkillOutcome] =
    Encoder
      .forProduct3("action", "status", "reason")((outcome: BattleCommandSkillOutcome) =>
        (
          SkillKind.wireValue(outcome.action),
          SkillOutcomeStatus.wireValue(outcome.outcomeStatus),
          outcome.reason.map(SkillOutcomeReason.wireValue)
        )
      )
      .mapJson(_.dropNullValues)

  given Encoder[BattleCommandAccepted] =
    Encoder
      .forProduct7("battleId", "acceptedTick", "acceptedCommandSeq", "serverTime", "commandStatus", "commandReason", "outcomes")(
        (response: BattleCommandAccepted) =>
          (
            response.battleId.value,
            response.acceptedTick.value,
            response.acceptedCommandSeq.value,
            response.serverTime.value,
            BattleCommandStatus.wireValue(response.commandStatus),
            response.commandReason.map(BattleCommandReason.wireValue),
            response.outcomes
          )
      )
      .mapJson(_.dropNullValues)
}
