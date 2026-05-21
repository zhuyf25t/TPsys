package slaydemo.backend.battle.objects.apiTypes

import io.circe.{Encoder, Json, JsonObject}
import io.circe.syntax.*

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest, BattleCommandSkillOutcome, BattleCommandVector}
import slaydemo.backend.battle.objects.*

enum BattleCommandAPIRequestError {
  case InvalidJsonObject
  case MissingTicket
  case BadRequest(code: String)
}

object BattleCommandRequestTarget {
  private val AllowedCommandPaths: Set[String] =
    Set(
      "/battle/command",
      "/battle/commands",
      "/api/battle/command",
      "/api/battle/commands",
      "/battlecommandapi",
      "/api/battlecommandapi"
    )

  def isCommandPath(path: String): Boolean =
    AllowedCommandPaths.contains(path)
}

final case class BattleCommandAPIRequest(
  battleId: String,
  playerId: String,
  ticketId: Option[String],
  clientTick: Long,
  clientCommandSeq: Option[Long],
  movement: BattleCommandVector,
  aim: BattleCommandVector,
  primaryHeld: Boolean,
  sprint: Option[Boolean],
  reloadPressed: Boolean,
  castDash: Option[Boolean],
  castBlink: Option[Boolean],
  castFreeze: Option[Boolean],
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: Int,
  switchWeaponIndex: Option[Int]
) {
  def toCommand: Either[BattleCommandAPIRequestError, BattleCommandRequest] =
    for
      battleIdValue <- nonEmptyText(battleId)
        .map(BattleId.apply)
        .toRight(BattleCommandAPIRequestError.BadRequest("missing_battle_id"))
      playerIdValue <- nonEmptyText(playerId)
        .map(PlayerId.apply)
        .toRight(BattleCommandAPIRequestError.BadRequest("missing_player_id"))
      ticketIdValue <- ticketId
        .flatMap(nonEmptyText)
        .map(TicketId.apply)
        .toRight(BattleCommandAPIRequestError.MissingTicket)
      switchDirection = BattleWeaponSwitchDirection.fromWire(switchWeaponDirection)
    yield BattleCommandRequest(
      battleId = battleIdValue,
      playerId = playerIdValue,
      ticketId = ticketIdValue,
      clientTick = BattleTick(clientTick),
      clientCommandSeq = ClientCommandSeq(clientCommandSeq.getOrElse(clientTick)),
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
      switchWeaponDirection = switchDirection,
      switchWeaponIndex = switchWeaponIndex.flatMap(BattleWeaponSwitchIndex.fromWire)
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

final case class BattleCommandSkillOutcomeResponse(
  action: String,
  status: String,
  reason: Option[String]
)

object BattleCommandSkillOutcomeResponse {
  given Encoder[BattleCommandSkillOutcomeResponse] =
    Encoder.instance { outcome =>
      Json.obj(
        (
          Vector(
            "action" -> Json.fromString(outcome.action),
            "status" -> Json.fromString(outcome.status)
          ) ++ optionalStringField("reason", outcome.reason)
        )*
      )
    }

  def fromOutcome(outcome: BattleCommandSkillOutcome): BattleCommandSkillOutcomeResponse =
    BattleCommandSkillOutcomeResponse(
      action = SkillKind.wireValue(outcome.action),
      status = SkillOutcomeStatus.wireValue(outcome.outcomeStatus),
      reason = outcome.reason.map(SkillOutcomeReason.wireValue)
    )
}

final case class BattleCommandAcceptedResponse(
  battleId: String,
  acceptedTick: Long,
  acceptedCommandSeq: Long,
  serverTime: Long,
  commandStatus: String,
  commandReason: Option[String],
  outcomes: Vector[BattleCommandSkillOutcomeResponse]
)

object BattleCommandAcceptedResponse {
  given Encoder[BattleCommandAcceptedResponse] =
    Encoder.instance { accepted =>
      Json.obj(
        (
          Vector(
            "battleId" -> Json.fromString(accepted.battleId),
            "acceptedTick" -> Json.fromLong(accepted.acceptedTick),
            "acceptedCommandSeq" -> Json.fromLong(accepted.acceptedCommandSeq),
            "serverTime" -> Json.fromLong(accepted.serverTime),
            "commandStatus" -> Json.fromString(accepted.commandStatus),
            "outcomes" -> Json.fromValues(accepted.outcomes.map(_.asJson))
          ) ++ optionalStringField("commandReason", accepted.commandReason)
        )*
      )
    }

  def fromAccepted(accepted: BattleCommandAccepted): BattleCommandAcceptedResponse =
    BattleCommandAcceptedResponse(
      battleId = accepted.battleId.value,
      acceptedTick = accepted.acceptedTick.value,
      acceptedCommandSeq = accepted.acceptedCommandSeq.value,
      serverTime = accepted.serverTime.value,
      commandStatus = BattleCommandStatus.wireValue(accepted.commandStatus),
      commandReason = accepted.commandReason.map(BattleCommandReason.wireValue),
      outcomes = accepted.outcomes.map(BattleCommandSkillOutcomeResponse.fromOutcome)
    )
}

private def optionalStringField(key: String, value: Option[String]): Vector[(String, Json)] =
  value.filter(_.trim.nonEmpty).map(text => Vector(key -> Json.fromString(text))).getOrElse(Vector.empty)

object BattleCommandAPIRequest {
  def decode(json: Json): Either[BattleCommandAPIRequestError, BattleCommandAPIRequest] =
    json.asObject.toRight(BattleCommandAPIRequestError.InvalidJsonObject).flatMap(decodeObject)

  def decodeCommand(json: Json): Either[BattleCommandAPIRequestError, BattleCommandRequest] =
    decode(json).flatMap(_.toCommand)

  private def decodeObject(fields: JsonObject): Either[BattleCommandAPIRequestError, BattleCommandAPIRequest] =
    for
      battleId <- requiredString(fields, "battleId", "missing_battle_id")
      playerId <- requiredString(fields, "playerId", "missing_player_id")
      ticketId = optionalString(fields, "ticketId")
      clientTick <- requiredLong(fields, "clientTick", "missing_client_tick")
      clientCommandSeq <- optionalLong(fields, "clientCommandSeq")
      movement <- requiredVector(fields, "movement", "missing_movement")
      aim <- requiredVector(fields, "aim", "missing_aim")
      primaryHeld <- requiredBoolean(fields, "primaryHeld", "missing_primary_held")
      sprint <- optionalBoolean(fields, "sprint")
      reloadPressed <- requiredBoolean(fields, "reloadPressed", "missing_reload_pressed")
      castDash <- optionalBoolean(fields, "castDash")
      castBlink <- optionalBoolean(fields, "castBlink")
      castFreeze <- optionalBoolean(fields, "castFreeze")
      pointerWorld <- optionalVector(fields, "pointerWorld")
      switchWeaponDirection <- requiredInt(fields, "switchWeaponDirection", "missing_switch_weapon_direction")
      switchWeaponIndex <- optionalInt(fields, "switchWeaponIndex")
    yield BattleCommandAPIRequest(
      battleId = battleId,
      playerId = playerId,
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq,
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = sprint,
      reloadPressed = reloadPressed,
      castDash = castDash,
      castBlink = castBlink,
      castFreeze = castFreeze,
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex
    )

  private def optionalString(fields: JsonObject, key: String): Option[String] =
    fields(key).flatMap(_.asString)

  private def requiredString(
    fields: JsonObject,
    key: String,
    errorCode: String
  ): Either[BattleCommandAPIRequestError, String] =
    optionalString(fields, key)
      .flatMap(nonEmptyText)
      .toRight(BattleCommandAPIRequestError.BadRequest(errorCode))

  private def optionalLong(fields: JsonObject, key: String): Either[BattleCommandAPIRequestError, Option[Long]] =
    fields(key) match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        numberAsLong(value).map(Some(_)).toRight(BattleCommandAPIRequestError.BadRequest(s"missing_${camelToSnake(key)}"))
    }

  private def requiredLong(
    fields: JsonObject,
    key: String,
    errorCode: String
  ): Either[BattleCommandAPIRequestError, Long] =
    fields(key)
      .flatMap(numberAsLong)
      .toRight(BattleCommandAPIRequestError.BadRequest(errorCode))

  private def optionalInt(fields: JsonObject, key: String): Either[BattleCommandAPIRequestError, Option[Int]] =
    fields(key) match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        numberAsInt(value).map(Some(_)).toRight(BattleCommandAPIRequestError.BadRequest(s"missing_${camelToSnake(key)}"))
    }

  private def requiredInt(
    fields: JsonObject,
    key: String,
    errorCode: String
  ): Either[BattleCommandAPIRequestError, Int] =
    fields(key)
      .flatMap(numberAsInt)
      .toRight(BattleCommandAPIRequestError.BadRequest(errorCode))

  private def optionalBoolean(fields: JsonObject, key: String): Either[BattleCommandAPIRequestError, Option[Boolean]] =
    fields(key) match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        value.asBoolean.map(Some(_)).toRight(BattleCommandAPIRequestError.BadRequest(s"missing_${camelToSnake(key)}"))
    }

  private def requiredBoolean(
    fields: JsonObject,
    key: String,
    errorCode: String
  ): Either[BattleCommandAPIRequestError, Boolean] =
    fields(key)
      .flatMap(_.asBoolean)
      .toRight(BattleCommandAPIRequestError.BadRequest(errorCode))

  private def optionalVector(fields: JsonObject, key: String): Either[BattleCommandAPIRequestError, Option[BattleCommandVector]] =
    fields(key) match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        decodeVector(value).map(Some(_)).left.map(_ => BattleCommandAPIRequestError.BadRequest(s"missing_${camelToSnake(key)}"))
    }

  private def requiredVector(
    fields: JsonObject,
    key: String,
    errorCode: String
  ): Either[BattleCommandAPIRequestError, BattleCommandVector] =
    fields(key) match {
      case Some(value) =>
        decodeVector(value).left.map(_ => BattleCommandAPIRequestError.BadRequest(errorCode))
      case None =>
        Left(BattleCommandAPIRequestError.BadRequest(errorCode))
    }

  private def decodeVector(json: Json): Either[BattleCommandAPIRequestError, BattleCommandVector] =
    json.asObject match {
      case None =>
        Left(BattleCommandAPIRequestError.InvalidJsonObject)
      case Some(fields) =>
        for
          x <- fields("x").flatMap(numberAsDouble).toRight(BattleCommandAPIRequestError.InvalidJsonObject)
          y <- fields("y").flatMap(numberAsDouble).toRight(BattleCommandAPIRequestError.InvalidJsonObject)
        yield BattleCommandVector(x, y)
    }

  private def numberAsLong(json: Json): Option[Long] =
    json.asNumber.flatMap(number => number.toLong.filter(value => value.toDouble == number.toDouble))

  private def numberAsInt(json: Json): Option[Int] =
    json.asNumber.flatMap(_.toInt)

  private def numberAsDouble(json: Json): Option[Double] =
    json.asNumber.map(_.toDouble).filter(_.isFinite)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def camelToSnake(value: String): String =
    value.flatMap {
      case char if char.isUpper =>
        "_" + char.toLower
      case char =>
        char.toString
    }
}
