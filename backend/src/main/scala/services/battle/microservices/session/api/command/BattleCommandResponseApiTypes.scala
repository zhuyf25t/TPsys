package services.battle.microservices.session.api.command

import io.circe.{Encoder, Json}
import io.circe.syntax.*
import services.battle.microservices.abilities.objects.skill.{SkillKind, SkillOutcomeReason, SkillOutcomeStatus}
import services.battle.microservices.session.objects.command.{
  BattleCommandAccepted,
  BattleCommandAcceptPath,
  BattleCommandServerDiagnostics,
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

  private given Encoder[BattleCommandAcceptPath] =
    Encoder.encodeString.contramap {
      case BattleCommandAcceptPath.Fresh      => "fresh"
      case BattleCommandAcceptPath.Serialized => "serialized"
    }

  private given Encoder[BattleCommandServerDiagnostics] =
    Encoder.instance { diagnostics =>
      Json.obj(
        "path" -> diagnostics.path.asJson,
        "receivedAt" -> diagnostics.receivedAt.value.asJson,
        "completedAt" -> diagnostics.completedAt.value.asJson,
        "durationMs" -> diagnostics.durationMs.asJson,
        "lockWaitMs" -> diagnostics.lockWaitMs.asJson,
        "lockHeldMs" -> diagnostics.lockHeldMs.asJson,
        "advanceMs" -> diagnostics.advanceMs.asJson,
        "commitRetryCount" -> diagnostics.commitRetryCount.asJson,
        "clientTick" -> diagnostics.clientTick.value.asJson,
        "acceptedTick" -> diagnostics.acceptedTick.value.asJson,
        "acceptedTickLag" -> diagnostics.acceptedTickLag.asJson,
        "clientCommandSeq" -> diagnostics.clientCommandSeq.value.asJson,
        "acceptedCommandSeq" -> diagnostics.acceptedCommandSeq.value.asJson,
        "acceptedCommandSeqLag" -> diagnostics.acceptedCommandSeqLag.asJson
      )
    }

  given Encoder[BattleCommandAccepted] =
    Encoder
      .forProduct8(
        "battleId",
        "acceptedTick",
        "acceptedCommandSeq",
        "serverTime",
        "commandStatus",
        "commandReason",
        "outcomes",
        "serverDiagnostics"
      )(
        (response: BattleCommandAccepted) =>
          (
            response.battleId.value,
            response.acceptedTick.value,
            response.acceptedCommandSeq.value,
            response.serverTime.value,
            BattleCommandStatus.wireValue(response.commandStatus),
            response.commandReason.map(BattleCommandReason.wireValue),
            response.outcomes,
            response.serverDiagnostics
          )
      )
      .mapJson(_.dropNullValues)
}
