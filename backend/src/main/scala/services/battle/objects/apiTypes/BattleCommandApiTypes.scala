package services.battle.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

import services.battle.objects.*

enum BattleCommandAPIRequestError {
  case InvalidJsonObject
  case MissingTicket
  case InvalidField(field: BattleCommandRequestField)
}

enum BattleCommandApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case CommandNotAuthorized
  case BattleNotFound
  case PlayerNotFound
  case BotCommandsNotSupported
  case InvalidField(field: BattleCommandRequestField)
}

object BattleCommandApiErrorCode {
  def fromRequestError(error: BattleCommandAPIRequestError): BattleCommandApiErrorCode =
    error match {
      case BattleCommandAPIRequestError.InvalidJsonObject =>
        BattleCommandApiErrorCode.InvalidJsonObject
      case BattleCommandAPIRequestError.MissingTicket =>
        BattleCommandApiErrorCode.CommandNotAuthorized
      case BattleCommandAPIRequestError.InvalidField(field) =>
        BattleCommandApiErrorCode.InvalidField(field)
    }

  def wireValue(code: BattleCommandApiErrorCode): String =
    code match {
      case BattleCommandApiErrorCode.MethodNotAllowed =>
        "method_not_allowed"
      case BattleCommandApiErrorCode.InvalidJsonObject =>
        "bad_request"
      case BattleCommandApiErrorCode.CommandNotAuthorized =>
        "command_not_authorized"
      case BattleCommandApiErrorCode.BattleNotFound =>
        "battle_not_found"
      case BattleCommandApiErrorCode.PlayerNotFound =>
        "player_not_found"
      case BattleCommandApiErrorCode.BotCommandsNotSupported =>
        "bot_commands_not_supported"
      case BattleCommandApiErrorCode.InvalidField(field) =>
        BattleCommandRequestField.errorCode(field)
    }

  def message(code: BattleCommandApiErrorCode): String =
    code match {
      case BattleCommandApiErrorCode.MethodNotAllowed =>
        "Only POST and OPTIONS are supported."
      case BattleCommandApiErrorCode.InvalidJsonObject =>
        "Request body must be a JSON object with supported primitive or object fields."
      case _ =>
        wireValue(code)
    }

  def statusCode(code: BattleCommandApiErrorCode): Int =
    code match {
      case BattleCommandApiErrorCode.MethodNotAllowed =>
        405
      case BattleCommandApiErrorCode.CommandNotAuthorized =>
        403
      case BattleCommandApiErrorCode.BattleNotFound =>
        404
      case _ =>
        400
    }
}

enum BattleCommandRequestField {
  case BattleId
  case PlayerId
  case ClientTick
  case ClientCommandSeq
  case Movement
  case Aim
  case PrimaryHeld
  case Sprint
  case ReloadPressed
  case CastDash
  case CastBlink
  case CastFreeze
  case PointerWorld
  case SwitchWeaponDirection
  case SwitchWeaponIndex
}

object BattleCommandRequestField {
  val all: Vector[BattleCommandRequestField] =
    Vector(
      BattleCommandRequestField.BattleId,
      BattleCommandRequestField.PlayerId,
      BattleCommandRequestField.ClientTick,
      BattleCommandRequestField.ClientCommandSeq,
      BattleCommandRequestField.Movement,
      BattleCommandRequestField.Aim,
      BattleCommandRequestField.PrimaryHeld,
      BattleCommandRequestField.Sprint,
      BattleCommandRequestField.ReloadPressed,
      BattleCommandRequestField.CastDash,
      BattleCommandRequestField.CastBlink,
      BattleCommandRequestField.CastFreeze,
      BattleCommandRequestField.PointerWorld,
      BattleCommandRequestField.SwitchWeaponDirection,
      BattleCommandRequestField.SwitchWeaponIndex
    )

  def fromErrorCode(value: String): Option[BattleCommandRequestField] =
    all.find(field => errorCode(field) == value)

  def errorCode(field: BattleCommandRequestField): String =
    field match {
      case BattleCommandRequestField.BattleId                => "missing_battle_id"
      case BattleCommandRequestField.PlayerId                => "missing_player_id"
      case BattleCommandRequestField.ClientTick              => "missing_client_tick"
      case BattleCommandRequestField.ClientCommandSeq        => "missing_client_command_seq"
      case BattleCommandRequestField.Movement                => "missing_movement"
      case BattleCommandRequestField.Aim                     => "missing_aim"
      case BattleCommandRequestField.PrimaryHeld             => "missing_primary_held"
      case BattleCommandRequestField.Sprint                  => "missing_sprint"
      case BattleCommandRequestField.ReloadPressed           => "missing_reload_pressed"
      case BattleCommandRequestField.CastDash                => "missing_cast_dash"
      case BattleCommandRequestField.CastBlink               => "missing_cast_blink"
      case BattleCommandRequestField.CastFreeze              => "missing_cast_freeze"
      case BattleCommandRequestField.PointerWorld            => "missing_pointer_world"
      case BattleCommandRequestField.SwitchWeaponDirection   => "missing_switch_weapon_direction"
      case BattleCommandRequestField.SwitchWeaponIndex       => "missing_switch_weapon_index"
    }
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
        .toRight(BattleCommandAPIRequestError.InvalidField(BattleCommandRequestField.BattleId))
      playerIdValue <- nonEmptyText(playerId)
        .map(PlayerId.apply)
        .toRight(BattleCommandAPIRequestError.InvalidField(BattleCommandRequestField.PlayerId))
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
    Encoder
      .forProduct3("action", "status", "reason")((response: BattleCommandSkillOutcomeResponse) =>
        (response.action, response.status, response.reason)
      )
      .mapJson(_.dropNullValues)

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
    Encoder
      .forProduct7("battleId", "acceptedTick", "acceptedCommandSeq", "serverTime", "commandStatus", "commandReason", "outcomes")(
        (response: BattleCommandAcceptedResponse) =>
          (
            response.battleId,
            response.acceptedTick,
            response.acceptedCommandSeq,
            response.serverTime,
            response.commandStatus,
            response.commandReason,
            response.outcomes
          )
      )
      .mapJson(_.dropNullValues)

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

object BattleCommandAPIRequest {
  def decode(json: Json): Either[BattleCommandAPIRequestError, BattleCommandAPIRequest] =
    if json.asObject.isEmpty then Left(BattleCommandAPIRequestError.InvalidJsonObject)
    else json.as[BattleCommandAPIRequestPayload].left.map(decodeFailure).map(_.toApiRequest)

  def decodeCommand(json: Json): Either[BattleCommandAPIRequestError, BattleCommandRequest] =
    decode(json).flatMap(_.toCommand)

  private def decodeFailure(error: DecodingFailure): BattleCommandAPIRequestError =
    BattleCommandRequestField.fromErrorCode(error.message) match {
      case Some(field) => BattleCommandAPIRequestError.InvalidField(field)
      case None        => BattleCommandAPIRequestError.InvalidJsonObject
    }
}

private final case class BattleCommandAPIRequestPayload(
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
  def toApiRequest: BattleCommandAPIRequest =
    BattleCommandAPIRequest(
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
}

private object BattleCommandAPIRequestPayload {
  given Decoder[BattleCommandVector] = Decoder.instance { cursor =>
    for
      x <- cursor.get[Double]("x").flatMap(finiteDouble("x"))
      y <- cursor.get[Double]("y").flatMap(finiteDouble("y"))
    yield BattleCommandVector(x, y)
  }

  given Decoder[BattleCommandAPIRequestPayload] = Decoder.instance { cursor =>
    for
      battleId <- required[String](cursor, "battleId", BattleCommandRequestField.BattleId).flatMap(nonEmptyString(BattleCommandRequestField.BattleId))
      playerId <- required[String](cursor, "playerId", BattleCommandRequestField.PlayerId).flatMap(nonEmptyString(BattleCommandRequestField.PlayerId))
      ticketId <- optionalTicketId(cursor)
      clientTick <- required[Long](cursor, "clientTick", BattleCommandRequestField.ClientTick)
      clientCommandSeq <- optional[Long](cursor, "clientCommandSeq", BattleCommandRequestField.ClientCommandSeq)
      movement <- required[BattleCommandVector](cursor, "movement", BattleCommandRequestField.Movement)
      aim <- required[BattleCommandVector](cursor, "aim", BattleCommandRequestField.Aim)
      primaryHeld <- required[Boolean](cursor, "primaryHeld", BattleCommandRequestField.PrimaryHeld)
      sprint <- optional[Boolean](cursor, "sprint", BattleCommandRequestField.Sprint)
      reloadPressed <- required[Boolean](cursor, "reloadPressed", BattleCommandRequestField.ReloadPressed)
      castDash <- optional[Boolean](cursor, "castDash", BattleCommandRequestField.CastDash)
      castBlink <- optional[Boolean](cursor, "castBlink", BattleCommandRequestField.CastBlink)
      castFreeze <- optional[Boolean](cursor, "castFreeze", BattleCommandRequestField.CastFreeze)
      pointerWorld <- optional[BattleCommandVector](cursor, "pointerWorld", BattleCommandRequestField.PointerWorld)
      switchWeaponDirection <- required[Int](cursor, "switchWeaponDirection", BattleCommandRequestField.SwitchWeaponDirection)
      switchWeaponIndex <- optional[Int](cursor, "switchWeaponIndex", BattleCommandRequestField.SwitchWeaponIndex)
    yield BattleCommandAPIRequestPayload(
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
  }

  private def required[A: Decoder](
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Decoder.Result[A] =
    cursor.get[A](key).left.map(_ => invalidField(field))

  private def optional[A: Decoder](
    cursor: HCursor,
    key: String,
    field: BattleCommandRequestField
  ): Decoder.Result[Option[A]] =
    cursor.get[Option[A]](key).left.map(_ => invalidField(field))

  private def optionalTicketId(cursor: HCursor): Decoder.Result[Option[String]] =
    cursor.downField("ticketId").focus match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) =>
        Right(value.asString)
    }

  private def nonEmptyString(field: BattleCommandRequestField)(value: String): Decoder.Result[String] =
    Option(value).map(_.trim).filter(_.nonEmpty).toRight(invalidField(field))

  private def finiteDouble(key: String)(value: Double): Decoder.Result[Double] =
    Either.cond(value.isFinite, value, DecodingFailure(s"$key must be finite", Nil))

  private def invalidField(field: BattleCommandRequestField): DecodingFailure =
    DecodingFailure(BattleCommandRequestField.errorCode(field), Nil)
}
