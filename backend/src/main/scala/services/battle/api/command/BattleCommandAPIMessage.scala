package services.battle.api.command

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, HCursor, Json}

import java.sql.Connection

import services.battle.database.session.{BattleCommandSubmitError, BattleStateService}
import services.battle.objects.{
  BattleAPIRequestError as BattleCommandAPIRequestError,
  BattleCommandAccepted,
  BattleCommandRequest,
  BattleCommandRequestField,
  BattleCommandSkillIntents,
  BattleCommandVector,
  BattleId,
  BattleTick,
  BattleWeaponSwitchDirection,
  BattleWeaponSwitchIndex,
  ClientCommandSeq,
  PlayerId,
  TicketId
}
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleCommandAPIMessage(
  userId: UserId,
  command: BattleCommandRequest
) extends APIWithTokenContextMessage[BattleStateService, BattleCommandAccepted] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleCommandAccepted] =
    for
      accepted <- acceptCommand(stateService, command)
    yield accepted

  private def acceptCommand(stateService: BattleStateService, command: BattleCommandRequest): IO[BattleCommandAccepted] =
    IO.blocking(stateService.acceptCommand(command)).flatMap {
      case Right(accepted) =>
        IO.pure(accepted)
      case Left(error) =>
        submitError(error)
    }

  private def submitError(error: BattleCommandSubmitError): IO[Nothing] =
    error match {
      case BattleCommandSubmitError.BattleNotFound =>
        IO.raiseError(APIMessageError.NotFound("battle_not_found"))
      case BattleCommandSubmitError.PlayerNotFound =>
        IO.raiseError(APIMessageError.BadRequest("player_not_found"))
      case BattleCommandSubmitError.BotCommandsNotSupported =>
        IO.raiseError(APIMessageError.BadRequest("bot_commands_not_supported"))
      case BattleCommandSubmitError.CommandNotAuthorized =>
        IO.raiseError(APIMessageError.Forbidden("command_not_authorized"))
    }
}

object BattleCommandAPIMessage {
  given Decoder[BattleCommandAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequest(cursor.value)
          .left
          .map(error => DecodingFailure(BattleCommandAPIRequestError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleCommandAPIMessage(userId, request)
    }

  private def decodeRequest(payload: Json): Either[BattleCommandAPIRequestError, BattleCommandRequest] =
    if payload.asObject.isEmpty then Left(BattleCommandAPIRequestError.InvalidJsonObject)
    else decodeRequestFields(payload.hcursor).left.map(commandRequestDecodeError)

  private def decodeRequestFields(cursor: HCursor): Either[DecodingFailure, BattleCommandRequest] =
    for
      battleId <- requiredText(cursor, "battleId", BattleCommandRequestField.BattleId).map(BattleId.apply)
      playerId <- requiredText(cursor, "playerId", BattleCommandRequestField.PlayerId).map(PlayerId.apply)
      ticketId <- requiredTicketId(cursor)
      clientTick <- required[Long](cursor, "clientTick", BattleCommandRequestField.ClientTick).map(BattleTick.apply)
      clientCommandSeq <- optional[Long](cursor, "clientCommandSeq", BattleCommandRequestField.ClientCommandSeq).map(_.map(ClientCommandSeq.apply))
      movement <- requiredVector(cursor, "movement", BattleCommandRequestField.Movement)
      aim <- requiredVector(cursor, "aim", BattleCommandRequestField.Aim)
      primaryHeld <- required[Boolean](cursor, "primaryHeld", BattleCommandRequestField.PrimaryHeld)
      sprint <- optional[Boolean](cursor, "sprint", BattleCommandRequestField.Sprint)
      reloadPressed <- required[Boolean](cursor, "reloadPressed", BattleCommandRequestField.ReloadPressed)
      castDash <- optional[Boolean](cursor, "castDash", BattleCommandRequestField.CastDash)
      castBlink <- optional[Boolean](cursor, "castBlink", BattleCommandRequestField.CastBlink)
      castFreeze <- optional[Boolean](cursor, "castFreeze", BattleCommandRequestField.CastFreeze)
      pointerWorld <- optionalVector(cursor, "pointerWorld", BattleCommandRequestField.PointerWorld)
      switchWeaponDirection <- required[Int](cursor, "switchWeaponDirection", BattleCommandRequestField.SwitchWeaponDirection)
        .map(BattleWeaponSwitchDirection.fromWire)
      switchWeaponIndex <- optional[Int](cursor, "switchWeaponIndex", BattleCommandRequestField.SwitchWeaponIndex)
        .map(_.flatMap(BattleWeaponSwitchIndex.fromWire))
    yield BattleCommandRequest(
      battleId = battleId,
      playerId = playerId,
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq.getOrElse(ClientCommandSeq(clientTick.value)),
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = sprint.getOrElse(false),
      reloadPressed = reloadPressed,
      skillIntents = BattleCommandSkillIntents.fromLegacyFlags(
        castDash = castDash.getOrElse(false),
        castBlink = castBlink.getOrElse(false),
        castFreeze = castFreeze.getOrElse(false)
      ),
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex
    )

  private def requiredText(
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Either[DecodingFailure, String] =
    required[String](cursor, key, field).flatMap { value =>
      Option(value).map(_.trim).filter(_.nonEmpty).toRight(invalidField(field, cursor))
    }

  private def required[A: Decoder](
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Either[DecodingFailure, A] =
    cursor.get[A](key).left.map(_ => invalidField(field, cursor))

  private def optional[A: Decoder](
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Either[DecodingFailure, Option[A]] =
    cursor.get[Option[A]](key).left.map(_ => invalidField(field, cursor))

  private def requiredTicketId(cursor: HCursor): Either[DecodingFailure, TicketId] =
    cursor
      .get[Option[String]]("ticketId")
      .toOption
      .flatten
      .flatMap(value => Option(value).map(_.trim).filter(_.nonEmpty))
      .map(TicketId.apply)
      .toRight(DecodingFailure(BattleCommandAPIRequestError.message(BattleCommandAPIRequestError.MissingTicket), cursor.history))

  private def requiredVector(
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Either[DecodingFailure, BattleCommandVector] =
    cursor.downField(key).focus
      .toRight(invalidField(field, cursor))
      .flatMap(decodeVector(_, field, cursor))

  private def optionalVector(
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Either[DecodingFailure, Option[BattleCommandVector]] =
    cursor.downField(key).focus match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        decodeVector(value, field, cursor).map(Some(_))
    }

  private def decodeVector(
    value: Json,
    field: BattleCommandRequestField,
    parentCursor: HCursor
  ): Either[DecodingFailure, BattleCommandVector] =
    for
      x <- requiredFiniteDouble(value.hcursor, "x", field, parentCursor)
      y <- requiredFiniteDouble(value.hcursor, "y", field, parentCursor)
    yield BattleCommandVector(x = x, y = y)

  private def requiredFiniteDouble(
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField,
    parentCursor: HCursor
  ): Either[DecodingFailure, Double] =
    cursor
      .get[Double](key)
      .left
      .map(_ => invalidField(field, parentCursor))
      .flatMap(parsed => Either.cond(parsed.isFinite, parsed, invalidField(field, parentCursor)))

  private def invalidField(field: BattleCommandRequestField, cursor: HCursor): DecodingFailure =
    DecodingFailure(field.toString, cursor.history)

  private def commandRequestDecodeError(error: io.circe.Error): BattleCommandAPIRequestError =
    error match {
      case failure: DecodingFailure
          if failure.message == BattleCommandAPIRequestError.message(BattleCommandAPIRequestError.MissingTicket) =>
        BattleCommandAPIRequestError.MissingTicket
      case failure: DecodingFailure =>
        BattleCommandRequestField
          .fromDecoderMessage(failure.message)
          .map(BattleCommandAPIRequestError.InvalidField.apply)
          .getOrElse(BattleCommandAPIRequestError.InvalidJsonObject)
      case _ =>
        BattleCommandAPIRequestError.InvalidJsonObject
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == "missing_ticket" =>
        APIMessageError.Forbidden("command_not_authorized")
      case failure: DecodingFailure =>
        BattleCommandRequestField.fromDecoderMessage(failure.message) match {
          case Some(field) =>
            APIMessageError.BadRequest(s"Invalid battle command field: $field")
          case None =>
            APIMessageError.BadRequest("Invalid battle command request.")
        }
      case _ =>
        APIMessageError.BadRequest("Invalid battle command request.")
    }
}
