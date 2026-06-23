package services.battle.microservices.runtime.api

import io.circe.Encoder
import io.circe.syntax.*
import services.battle.microservices.abilities.api.BattleSkillOutcomeAPIEncoding.given
import services.battle.microservices.runtime.objects.command.{
  BattleCommandAccepted,
  BattleCommandAcceptPath,
  BattleCommandReason,
  BattleCommandServerDiagnostics,
  BattleCommandStatus
}

object BattleCommandAcceptedAPIEncoding {
  private given Encoder[BattleCommandAcceptPath] =
    Encoder.encodeString.contramap {
      case BattleCommandAcceptPath.Fresh      => "fresh"
      case BattleCommandAcceptPath.Serialized => "serialized"
    }

  private given Encoder[BattleCommandServerDiagnostics] =
    Encoder.forProduct14(
      "path",
      "receivedAt",
      "completedAt",
      "durationMs",
      "lockWaitMs",
      "lockHeldMs",
      "advanceMs",
      "commitRetryCount",
      "clientTick",
      "acceptedTick",
      "acceptedTickLag",
      "clientCommandSeq",
      "acceptedCommandSeq",
      "acceptedCommandSeqLag"
    )(
      (diagnostics: BattleCommandServerDiagnostics) =>
        (
          diagnostics.path,
          diagnostics.receivedAt.value,
          diagnostics.completedAt.value,
          diagnostics.durationMs,
          diagnostics.lockWaitMs,
          diagnostics.lockHeldMs,
          diagnostics.advanceMs,
          diagnostics.commitRetryCount,
          diagnostics.clientTick.value,
          diagnostics.acceptedTick.value,
          diagnostics.acceptedTickLag,
          diagnostics.clientCommandSeq.value,
          diagnostics.acceptedCommandSeq.value,
          diagnostics.acceptedCommandSeqLag
        )
      )

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
