package services.battle.microservices.session.api.command

import io.circe.{Decoder, DecodingFailure, Error, HCursor, Json}

import services.battle.microservices.session.objects.command.{BattleCommandRequest, BattleCommandVector}
import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, PlayerId}
import services.battle.microservices.abilities.objects.skill.BattleCommandSkillIntents
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

object BattleCommandRequestPayload {
  given Decoder[BattleCommandRequest] =
    Decoder.instance(decodeRequestFields)

  def decode(payload: Json): Either[BattleCommandRequestDecodeError, BattleCommandRequest] =
    if payload.asObject.isEmpty then Left(BattleCommandRequestDecodeError.InvalidJsonObject)
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
      .toRight(DecodingFailure(BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.MissingTicket), cursor.history))

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

  private def commandRequestDecodeError(error: Error): BattleCommandRequestDecodeError =
    error match {
      case failure: DecodingFailure
          if failure.message == BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.MissingTicket) =>
        BattleCommandRequestDecodeError.MissingTicket
      case failure: DecodingFailure =>
        BattleCommandRequestField
          .fromDecoderMessage(failure.message)
          .map(BattleCommandRequestDecodeError.InvalidField.apply)
          .getOrElse(BattleCommandRequestDecodeError.InvalidJsonObject)
      case _ =>
        BattleCommandRequestDecodeError.InvalidJsonObject
    }
}
