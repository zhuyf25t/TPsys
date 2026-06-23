package services.battle.microservices.runtime.api

import cats.effect.IO
import io.circe.{Decoder, Error, Json}
import io.circe.parser.parse

import java.sql.Connection

import services.battle.microservices.abilities.objects.skill.BattleCommandSkillIntents
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}
import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.runtime.objects.command.{
  BattleCommandAccepted,
  BattleCommandInputState,
  BattleCommandRequest,
  BattleCommandVector
}
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, PlayerId}
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleCommandAPIMessage(
  userId: UserId,
  battleId: BattleId,
  playerId: PlayerId,
  ticketId: TicketId,
  clientTick: BattleTick,
  clientCommandSeq: ClientCommandSeq,
  movement: BattleCommandVector,
  aim: BattleCommandVector,
  inputState: BattleCommandInputState,
  skillIntents: BattleCommandSkillIntents,
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: BattleWeaponSwitchDirection,
  switchWeaponIndex: Option[BattleWeaponSwitchIndex]
) extends APIWithTokenContextMessage[BattleStateService, BattleCommandAccepted] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleCommandAccepted] =
    BattleCommandAPIPlanner.plan(stateService, this)

  def toCommandRequest: BattleCommandRequest =
    BattleCommandAPIMessageDecoding.toCommandRequest(this)
}

object BattleCommandAPIMessage {
  private[runtime] def submitCommand(
    stateService: BattleStateService,
    command: BattleCommandRequest
  ): IO[BattleCommandAccepted] =
    BattleCommandAPIPlanner.submitCommand(stateService, command)

  def fromCommandRequest(userId: UserId, command: BattleCommandRequest): BattleCommandAPIMessage =
    BattleCommandAPIMessageDecoding.fromCommandRequest(userId, command)

  given Decoder[BattleCommandAPIMessage] =
    BattleCommandAPIMessageDecoding.apiMessageDecoder

  given Decoder[BattleCommandRequest] =
    BattleCommandAPIMessageDecoding.commandRequestDecoder

  def decodeCommandPayload(payload: Json): Either[BattleCommandRequestDecodeError, BattleCommandRequest] =
    if payload.asObject.isEmpty then Left(BattleCommandRequestDecodeError.InvalidJsonObject)
    else payload.as[BattleCommandRequest].left.map(commandRequestDecodeError)

  def decodeCommandText(text: String): Either[BattleCommandRequestDecodeError, BattleCommandRequest] =
    parse(text).left
      .map(_ => BattleCommandRequestDecodeError.InvalidJsonObject)
      .flatMap(decodeCommandPayload)

  def commandDecodeErrorCode(error: BattleCommandRequestDecodeError): String =
    error match {
      case BattleCommandRequestDecodeError.MissingTicket =>
        "command_not_authorized"
      case BattleCommandRequestDecodeError.InvalidJsonObject =>
        "invalid_battle_command_request"
      case BattleCommandRequestDecodeError.InvalidField(field) =>
        s"invalid_battle_command_field_${field.toString}"
    }

  def commandRequestDecodeError(error: Throwable): BattleCommandRequestDecodeError =
    val messages = throwableMessages(error)
    if messages.exists(_.contains(BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.MissingTicket))) then
      BattleCommandRequestDecodeError.MissingTicket
    else
      BattleCommandRequestField.values
        .find(field => messages.exists(_.contains(field.toString)))
        .map(BattleCommandRequestDecodeError.InvalidField.apply)
        .getOrElse(BattleCommandRequestDecodeError.InvalidJsonObject)

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleRuntimeAPIMessageErrors.commandDecodeFailure(error)

  private def throwableMessages(error: Throwable): Vector[String] =
    Iterator
      .iterate(error)(_.getCause)
      .takeWhile(_ != null)
      .map(error => Option(error.getMessage).getOrElse(""))
      .toVector
}
