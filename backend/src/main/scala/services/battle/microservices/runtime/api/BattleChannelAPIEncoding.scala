package services.battle.microservices.runtime.api

import io.circe.{Encoder, Json}
import io.circe.syntax.*

import services.battle.microservices.runtime.objects.command.BattleCommandAccepted
import services.battle.objects.core.BattleAggregateState

object BattleChannelAPIEncoding {
  import BattleAggregateStateAPIEncoding.given
  import BattleCommandAcceptedAPIEncoding.given

  def commandAcceptedJson(accepted: BattleCommandAccepted): String =
    accepted.asJson.noSpaces

  def commandErrorJson(code: String): String =
    commandErrorPayload(code).noSpaces

  def battleCommandAcceptedMessage(accepted: BattleCommandAccepted): String =
    battleCommandMessage(accepted.asJson)

  def battleCommandErrorMessage(code: String): String =
    battleCommandMessage(commandErrorPayload(code))

  def battleStateMessage(state: BattleAggregateState): String =
    BattleStateEnvelope(kind = "state", state = state).asJson.noSpaces

  def stateJson(state: BattleAggregateState): Json =
    state.asJson

  def stateEvent(state: BattleAggregateState): String =
    s"event: state\ndata: ${state.asJson.noSpaces}\n\n"

  private def commandErrorPayload(code: String): Json =
    BattleCommandErrorPayload(message = code).asJson

  private def battleCommandMessage(payload: Json): String =
    BattleCommandEnvelope(kind = "command", payload = payload).asJson.noSpaces

  private final case class BattleCommandErrorPayload(message: String)

  private object BattleCommandErrorPayload:
    given Encoder[BattleCommandErrorPayload] =
      Encoder.forProduct1("message")(_.message)

  private final case class BattleCommandEnvelope(kind: String, payload: Json)

  private object BattleCommandEnvelope:
    given Encoder[BattleCommandEnvelope] =
      Encoder.forProduct2("kind", "payload")(envelope => (envelope.kind, envelope.payload))

  private final case class BattleStateEnvelope(kind: String, state: BattleAggregateState)

  private object BattleStateEnvelope:
    given Encoder[BattleStateEnvelope] =
      Encoder.forProduct2("kind", "state")(envelope => (envelope.kind, envelope.state))
}
