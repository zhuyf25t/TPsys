package services.battle.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto.deriveEncoder

import services.battle.objects.*
import services.battle.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinCommand,
  BattleQueueLeaveOutcome,
  BattleQueueStatusError,
  BattleRoomError,
  RealtimeRoomHeartbeatCommand
}
import services.identity.objects.{PlayerHandle, SessionToken}

enum BattleQueueJoinAPIRequestError {
  case InvalidJsonObject
  case InvalidRating
  case InvalidHandle
  case MissingSession
}

enum BattleQueueLeaveAPIRequestError {
  case InvalidJsonObject
  case MissingTicketId
}

enum BattleQueueApiErrorCode {
  case InvalidJsonObject
  case MissingStatusTicketId
  case MissingLeaveTicketId
  case TicketNotFound
  case InvalidHandle
  case InvalidRating
  case MissingSession
  case InvalidSession
  case IdentityMismatch
  case StatusMethodNotAllowed
  case PostMethodNotAllowed
}

object BattleQueueApiErrorCode {
  def fromStatusError(error: BattleQueueStatusError): BattleQueueApiErrorCode =
    error match {
      case BattleQueueStatusError.TicketNotFound =>
        BattleQueueApiErrorCode.TicketNotFound
    }

  def fromJoinRequestError(error: BattleQueueJoinAPIRequestError): BattleQueueApiErrorCode =
    error match {
      case BattleQueueJoinAPIRequestError.InvalidJsonObject =>
        BattleQueueApiErrorCode.InvalidJsonObject
      case BattleQueueJoinAPIRequestError.InvalidRating =>
        BattleQueueApiErrorCode.InvalidRating
      case BattleQueueJoinAPIRequestError.InvalidHandle =>
        BattleQueueApiErrorCode.InvalidHandle
      case BattleQueueJoinAPIRequestError.MissingSession =>
        BattleQueueApiErrorCode.MissingSession
    }

  def fromJoinAuthorizationError(error: BattleQueueJoinAuthorizationError): BattleQueueApiErrorCode =
    error match {
      case BattleQueueJoinAuthorizationError.InvalidSession =>
        BattleQueueApiErrorCode.InvalidSession
      case BattleQueueJoinAuthorizationError.HandleMismatch =>
        BattleQueueApiErrorCode.IdentityMismatch
    }

  def fromLeaveRequestError(error: BattleQueueLeaveAPIRequestError): BattleQueueApiErrorCode =
    error match {
      case BattleQueueLeaveAPIRequestError.InvalidJsonObject =>
        BattleQueueApiErrorCode.InvalidJsonObject
      case BattleQueueLeaveAPIRequestError.MissingTicketId =>
        BattleQueueApiErrorCode.MissingLeaveTicketId
    }

  def wireValue(code: BattleQueueApiErrorCode): String =
    code match {
      case BattleQueueApiErrorCode.InvalidJsonObject =>
        "bad_request"
      case BattleQueueApiErrorCode.MissingStatusTicketId =>
        "missing_ticket_id"
      case BattleQueueApiErrorCode.MissingLeaveTicketId =>
        "bad_request"
      case BattleQueueApiErrorCode.TicketNotFound =>
        "ticket_not_found"
      case BattleQueueApiErrorCode.InvalidHandle =>
        "invalid_handle"
      case BattleQueueApiErrorCode.InvalidRating =>
        "bad_request"
      case BattleQueueApiErrorCode.MissingSession =>
        "missing_session"
      case BattleQueueApiErrorCode.InvalidSession =>
        "invalid_session"
      case BattleQueueApiErrorCode.IdentityMismatch =>
        "identity_mismatch"
      case BattleQueueApiErrorCode.StatusMethodNotAllowed =>
        "method_not_allowed"
      case BattleQueueApiErrorCode.PostMethodNotAllowed =>
        "method_not_allowed"
    }

  def message(code: BattleQueueApiErrorCode): String =
    code match {
      case BattleQueueApiErrorCode.InvalidJsonObject =>
        "Request body must be a JSON object with supported primitive or object fields."
      case BattleQueueApiErrorCode.MissingStatusTicketId =>
        "ticketId query parameter is required."
      case BattleQueueApiErrorCode.MissingLeaveTicketId =>
        "ticketId is required."
      case BattleQueueApiErrorCode.TicketNotFound =>
        "Queue ticket was not found."
      case BattleQueueApiErrorCode.InvalidHandle =>
        "Handle must be a playable non-visitor handle."
      case BattleQueueApiErrorCode.InvalidRating =>
        "rating must be an integer."
      case BattleQueueApiErrorCode.MissingSession =>
        "Session token is required."
      case BattleQueueApiErrorCode.InvalidSession =>
        "Session token is not valid."
      case BattleQueueApiErrorCode.IdentityMismatch =>
        "Session does not belong to the requested handle."
      case BattleQueueApiErrorCode.StatusMethodNotAllowed =>
        "Only GET and OPTIONS are supported."
      case BattleQueueApiErrorCode.PostMethodNotAllowed =>
        "Only POST and OPTIONS are supported."
    }

  def statusCode(code: BattleQueueApiErrorCode): Int =
    code match {
      case BattleQueueApiErrorCode.TicketNotFound =>
        404
      case BattleQueueApiErrorCode.MissingSession | BattleQueueApiErrorCode.InvalidSession =>
        401
      case BattleQueueApiErrorCode.IdentityMismatch =>
        403
      case BattleQueueApiErrorCode.StatusMethodNotAllowed | BattleQueueApiErrorCode.PostMethodNotAllowed =>
        405
      case _ =>
        400
    }
}

final case class BattleQueueJoinAPIRequest(
  handle: Option[String],
  sessionToken: Option[String],
  queueRequestId: Option[String],
  rating: Option[Int],
  avatar: Option[String],
  skin: Option[String]
) {
  def toCommand: Either[BattleQueueJoinAPIRequestError, BattleQueueJoinCommand] =
    for
      playerHandle <- PlayerHandle.forLookup(handle.getOrElse(""))
        .toRight(BattleQueueJoinAPIRequestError.InvalidHandle)
      session <- SessionToken.fromString(sessionToken.getOrElse(""))
        .toRight(BattleQueueJoinAPIRequestError.MissingSession)
    yield BattleQueueJoinCommand(
      handle = playerHandle,
      sessionToken = session,
      queueRequestId = queueRequestId.flatMap(nonEmptyText).map(QueueRequestId.apply),
      rating = rating.map(Rating.apply),
      avatar = avatar.flatMap(nonEmptyText),
      skin = skin.flatMap(nonEmptyText)
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

final case class BattleQueueLeaveAPIRequest(ticketId: Option[String]) {
  def toTicketId: Either[BattleQueueLeaveAPIRequestError, TicketId] =
    ticketId
      .flatMap(nonEmptyText)
      .map(TicketId.apply)
      .toRight(BattleQueueLeaveAPIRequestError.MissingTicketId)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

object BattleQueueLeaveAPIRequest {
  given Decoder[BattleQueueLeaveAPIRequest] = (cursor: HCursor) =>
    BattleQueueJoinAPIRequest.optionalText(cursor, "ticketId").map(BattleQueueLeaveAPIRequest.apply)

  def decodeTicketId(json: Json): Either[BattleQueueLeaveAPIRequestError, TicketId] =
    json.as[BattleQueueLeaveAPIRequest]
      .left.map(_ => BattleQueueLeaveAPIRequestError.InvalidJsonObject)
      .flatMap(_.toTicketId)
}

enum BattleQueueLeaveAPIOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}

object BattleQueueLeaveAPIOutcome {
  def fromLeaveOutcome(outcome: BattleQueueLeaveOutcome): BattleQueueLeaveAPIOutcome =
    outcome match {
      case BattleQueueLeaveOutcome.LeftQueue =>
        BattleQueueLeaveAPIOutcome.LeftQueue
      case BattleQueueLeaveOutcome.NotWaiting =>
        BattleQueueLeaveAPIOutcome.NotWaiting
      case BattleQueueLeaveOutcome.TicketNotFound =>
        BattleQueueLeaveAPIOutcome.TicketNotFound
    }

  def leftFlag(outcome: BattleQueueLeaveAPIOutcome): Boolean =
    outcome match {
      case BattleQueueLeaveAPIOutcome.LeftQueue      => true
      case BattleQueueLeaveAPIOutcome.NotWaiting     => false
      case BattleQueueLeaveAPIOutcome.TicketNotFound => false
    }
}

final case class BattleQueueLeaveAPIResponse(outcome: BattleQueueLeaveAPIOutcome)

object BattleQueueLeaveAPIResponse {
  given Encoder[BattleQueueLeaveAPIResponse] =
    Encoder.forProduct1("left")(response => BattleQueueLeaveAPIOutcome.leftFlag(response.outcome))

  def fromOutcome(outcome: BattleQueueLeaveOutcome): BattleQueueLeaveAPIResponse =
    BattleQueueLeaveAPIResponse(BattleQueueLeaveAPIOutcome.fromLeaveOutcome(outcome))
}

final case class RealtimeRoomHeartbeatAPIRequest(
  roomId: Option[String],
  ticketId: Option[String],
  handle: Option[String]
) {
  def toCommand(
    pathRoomId: Option[RoomId],
    query: Map[String, String]
  ): RealtimeRoomHeartbeatCommand =
    RealtimeRoomHeartbeatCommand(
      roomId = pathRoomId
        .orElse(roomId.flatMap(nonEmptyText).map(RoomId.apply))
        .orElse(query.get("roomId").flatMap(nonEmptyText).map(RoomId.apply)),
      ticketId = ticketId
        .flatMap(nonEmptyText)
        .map(TicketId.apply)
        .orElse(query.get("ticketId").flatMap(nonEmptyText).map(TicketId.apply)),
      handle = handle
        .flatMap(nonEmptyText)
        .orElse(query.get("handle").flatMap(nonEmptyText))
        .flatMap(PlayerHandle.forLookup)
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

enum RealtimeRoomHeartbeatAPIRequestError {
  case InvalidJsonObject
}

enum BattleRoomApiErrorCode {
  case InvalidRoomId
  case InvalidJsonObject
  case RoomNotFound
  case SnapshotMethodNotAllowed
  case HeartbeatMethodNotAllowed
}

object BattleRoomApiErrorCode {
  def fromHeartbeatRequestError(error: RealtimeRoomHeartbeatAPIRequestError): BattleRoomApiErrorCode =
    error match {
      case RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject =>
        BattleRoomApiErrorCode.InvalidJsonObject
    }

  def fromRoomError(error: BattleRoomError): BattleRoomApiErrorCode =
    error match {
      case BattleRoomError.MissingRoomId =>
        BattleRoomApiErrorCode.InvalidRoomId
      case BattleRoomError.RoomNotFound =>
        BattleRoomApiErrorCode.RoomNotFound
    }

  def wireValue(code: BattleRoomApiErrorCode): String =
    code match {
      case BattleRoomApiErrorCode.InvalidRoomId =>
        "invalid_room_id"
      case BattleRoomApiErrorCode.InvalidJsonObject =>
        "bad_request"
      case BattleRoomApiErrorCode.RoomNotFound =>
        "room_not_found"
      case BattleRoomApiErrorCode.SnapshotMethodNotAllowed =>
        "method_not_allowed"
      case BattleRoomApiErrorCode.HeartbeatMethodNotAllowed =>
        "method_not_allowed"
    }

  def message(code: BattleRoomApiErrorCode): String =
    code match {
      case BattleRoomApiErrorCode.InvalidRoomId =>
        "roomId is required."
      case BattleRoomApiErrorCode.InvalidJsonObject =>
        "Request body must be a JSON object with supported primitive or object fields."
      case BattleRoomApiErrorCode.RoomNotFound =>
        "Battle room was not found."
      case BattleRoomApiErrorCode.SnapshotMethodNotAllowed =>
        "Only GET and OPTIONS are supported."
      case BattleRoomApiErrorCode.HeartbeatMethodNotAllowed =>
        "Only POST and OPTIONS are supported."
    }

  def statusCode(code: BattleRoomApiErrorCode): Int =
    code match {
      case BattleRoomApiErrorCode.RoomNotFound =>
        404
      case BattleRoomApiErrorCode.SnapshotMethodNotAllowed | BattleRoomApiErrorCode.HeartbeatMethodNotAllowed =>
        405
      case _ =>
        400
    }
}

object RealtimeRoomHeartbeatAPIRequest {
  given Decoder[RealtimeRoomHeartbeatAPIRequest] = (cursor: HCursor) =>
    for
      roomId <- BattleQueueJoinAPIRequest.optionalText(cursor, "roomId")
      ticketId <- BattleQueueJoinAPIRequest.optionalText(cursor, "ticketId")
      handle <- BattleQueueJoinAPIRequest.optionalText(cursor, "handle")
    yield RealtimeRoomHeartbeatAPIRequest(roomId = roomId, ticketId = ticketId, handle = handle)

  def decodeCommand(
    json: Json,
    pathRoomId: Option[RoomId],
    query: Map[String, String]
  ): Either[RealtimeRoomHeartbeatAPIRequestError, RealtimeRoomHeartbeatCommand] =
    json.as[RealtimeRoomHeartbeatAPIRequest]
      .left.map(_ => RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject)
      .map(_.toCommand(pathRoomId, query))
}

final case class RealtimeRoomSnapshotResponse(
  roomId: String,
  serverTime: Long,
  participants: Vector[BattleQueueParticipantResponse],
  capacity: Int,
  phase: String,
  finishedAt: Option[Long],
  battleSession: Option[BattleSessionDescriptorResponse]
)

object RealtimeRoomSnapshotResponse {
  given Encoder[RealtimeRoomSnapshotResponse] = deriveEncoder

  def fromSnapshot(snapshot: RealtimeRoomSnapshot): RealtimeRoomSnapshotResponse =
    RealtimeRoomSnapshotResponse(
      roomId = snapshot.roomId.value,
      serverTime = snapshot.serverTime.value,
      participants = snapshot.participants.map(BattleQueueParticipantResponse.fromParticipant),
      capacity = snapshot.capacity.value,
      phase = MatchmakingRoomPhase.wireValue(snapshot.phase),
      finishedAt = snapshot.finishedAt.map(_.value),
      battleSession = snapshot.battleSession.map(BattleSessionDescriptorResponse.fromSession)
    )
}

object BattleQueueJoinAPIRequest {
  given Decoder[BattleQueueJoinAPIRequest] = (cursor: HCursor) =>
    for
      handle <- optionalText(cursor, "handle")
      sessionToken <- optionalText(cursor, "sessionToken")
      queueRequestId <- optionalText(cursor, "queueRequestId")
      rating <- optionalInt(cursor, "rating")
      avatar <- optionalText(cursor, "avatar")
      skin <- optionalText(cursor, "skin")
    yield BattleQueueJoinAPIRequest(
      handle = handle,
      sessionToken = sessionToken,
      queueRequestId = queueRequestId,
      rating = rating,
      avatar = avatar,
      skin = skin
    )

  def optionalText(cursor: HCursor, field: String): Decoder.Result[Option[String]] =
    cursor.downField(field).focus match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) if value.isString =>
        Right(value.asString)
      case Some(value) if value.isNumber =>
        value.asNumber.flatMap(_.toLong).map(number => Right(Some(number.toString))).getOrElse(Right(Some(value.noSpaces)))
      case _ =>
        Right(None)
    }

  private def optionalInt(cursor: HCursor, field: String): Decoder.Result[Option[Int]] =
    cursor.downField(field).focus match {
      case None =>
        Right(None)
      case Some(value) if value.isNull =>
        Right(None)
      case Some(value) if value.isString =>
        value.asString.map(_.trim).filter(_.nonEmpty) match {
          case None =>
            Right(None)
          case Some(trimmed) =>
            trimmed.toIntOption.map(value => Right(Some(value))).getOrElse(Left(DecodingFailure(s"$field must be an integer.", cursor.history)))
        }
      case Some(value) if value.isNumber =>
        value.asNumber.flatMap(_.toInt).map(value => Right(Some(value))).getOrElse(Left(DecodingFailure(s"$field must fit in a 32-bit integer.", cursor.history)))
      case _ =>
        Left(DecodingFailure(s"$field must be an integer.", cursor.history))
    }

  def decodeCommand(json: Json): Either[BattleQueueJoinAPIRequestError, BattleQueueJoinCommand] =
    json.as[BattleQueueJoinAPIRequest]
      .left.map(_ => BattleQueueJoinAPIRequestError.InvalidRating)
      .flatMap(_.toCommand)
}

final case class BattleQueueParticipantResponse(
  playerId: String,
  handle: String,
  joinedAt: Long,
  lastSeen: Long,
  rating: Option[Int],
  avatar: Option[String],
  skin: Option[String]
)

object BattleQueueParticipantResponse {
  given Encoder[BattleQueueParticipantResponse] =
    Encoder
      .forProduct7("playerId", "handle", "joinedAt", "lastSeen", "rating", "avatar", "skin")(
        (value: BattleQueueParticipantResponse) =>
          (value.playerId, value.handle, value.joinedAt, value.lastSeen, value.rating, value.avatar, value.skin)
      )
      .mapJson(_.dropNullValues)

  def fromParticipant(participant: BattleQueueParticipant): BattleQueueParticipantResponse =
    BattleQueueParticipantResponse(
      playerId = participant.playerId.value,
      handle = participant.handle.value,
      joinedAt = participant.joinedAt.value,
      lastSeen = participant.lastSeen.value,
      rating = participant.rating.map(_.value),
      avatar = participant.avatar,
      skin = participant.skin
    )
}

final case class BattleSessionRosterEntryResponse(
  seat: Int,
  playerId: String,
  handle: String,
  joinedAt: Long,
  rating: Option[Int],
  avatar: Option[String],
  skin: Option[String]
)

object BattleSessionRosterEntryResponse {
  given Encoder[BattleSessionRosterEntryResponse] =
    Encoder
      .forProduct7("seat", "playerId", "handle", "joinedAt", "rating", "avatar", "skin")(
        (value: BattleSessionRosterEntryResponse) =>
          (value.seat, value.playerId, value.handle, value.joinedAt, value.rating, value.avatar, value.skin)
      )
      .mapJson(_.dropNullValues)

  def fromEntry(entry: BattleSessionRosterEntry): BattleSessionRosterEntryResponse =
    BattleSessionRosterEntryResponse(
      seat = entry.seat.value,
      playerId = entry.playerId.value,
      handle = entry.handle.value,
      joinedAt = entry.joinedAt.value,
      rating = entry.rating.map(_.value),
      avatar = entry.avatar,
      skin = entry.skin
    )
}

final case class BattleSessionBootstrapSeatResponse(
  seat: Int,
  playerId: String,
  heroId: String,
  handle: String,
  displayName: String,
  joinedAt: Long,
  isBot: Boolean,
  spawnPointIndex: Int,
  rating: Option[Int],
  avatar: Option[String],
  skin: Option[String]
)

object BattleSessionBootstrapSeatResponse {
  given Encoder[BattleSessionBootstrapSeatResponse] =
    Encoder
      .forProduct11(
        "seat",
        "playerId",
        "heroId",
        "handle",
        "displayName",
        "joinedAt",
        "isBot",
        "spawnPointIndex",
        "rating",
        "avatar",
        "skin"
      )(
        (value: BattleSessionBootstrapSeatResponse) =>
          (
            value.seat,
            value.playerId,
            value.heroId,
            value.handle,
            value.displayName,
            value.joinedAt,
            value.isBot,
            value.spawnPointIndex,
            value.rating,
            value.avatar,
            value.skin
          )
      )
      .mapJson(_.dropNullValues)

  def fromSeat(seat: BattleSessionBootstrapSeat): BattleSessionBootstrapSeatResponse =
    BattleSessionBootstrapSeatResponse(
      seat = seat.seat.value,
      playerId = seat.playerId.value,
      heroId = seat.heroId.value,
      handle = seat.handle.value,
      displayName = seat.displayName.value,
      joinedAt = seat.joinedAt.value,
      isBot = seat.isBot,
      spawnPointIndex = seat.spawnPointIndex.value,
      rating = seat.rating.map(_.value),
      avatar = seat.avatar,
      skin = seat.skin
    )
}

final case class BattleSessionBootstrapResponse(seats: Vector[BattleSessionBootstrapSeatResponse])

object BattleSessionBootstrapResponse {
  given Encoder[BattleSessionBootstrapResponse] = deriveEncoder

  def fromBootstrap(bootstrap: BattleSessionBootstrap): BattleSessionBootstrapResponse =
    BattleSessionBootstrapResponse(bootstrap.seats.map(BattleSessionBootstrapSeatResponse.fromSeat))
}

final case class BattleSessionDescriptorResponse(
  battleId: String,
  startedAt: Long,
  serverTime: Long,
  roster: Vector[BattleSessionRosterEntryResponse],
  capacity: Int,
  bootstrap: Option[BattleSessionBootstrapResponse]
)

object BattleSessionDescriptorResponse {
  given Encoder[BattleSessionDescriptorResponse] = deriveEncoder

  def fromSession(session: BattleSessionDescriptor): BattleSessionDescriptorResponse =
    BattleSessionDescriptorResponse(
      battleId = session.battleId.value,
      startedAt = session.startedAt.value,
      serverTime = session.serverTime.value,
      roster = session.roster.map(BattleSessionRosterEntryResponse.fromEntry),
      capacity = session.capacity.value,
      bootstrap = session.bootstrap.map(BattleSessionBootstrapResponse.fromBootstrap)
    )
}

final case class BattleQueueSnapshotResponse(
  ticketId: String,
  playerId: String,
  roomId: String,
  createdAt: Long,
  startsAt: Long,
  deadline: Long,
  serverTime: Long,
  participants: Vector[BattleQueueParticipantResponse],
  capacity: Int,
  durationMs: Long,
  phase: String,
  finishedAt: Option[Long],
  battleSession: Option[BattleSessionDescriptorResponse]
)

object BattleQueueSnapshotResponse {
  given Encoder[BattleQueueSnapshotResponse] = deriveEncoder

  def fromSnapshot(snapshot: BattleQueueSnapshot): BattleQueueSnapshotResponse =
    BattleQueueSnapshotResponse(
      ticketId = snapshot.ticketId.value,
      playerId = snapshot.playerId.value,
      roomId = snapshot.roomId.value,
      createdAt = snapshot.createdAt.value,
      startsAt = snapshot.startsAt.value,
      deadline = snapshot.deadline.value,
      serverTime = snapshot.serverTime.value,
      participants = snapshot.participants.map(BattleQueueParticipantResponse.fromParticipant),
      capacity = snapshot.capacity.value,
      durationMs = snapshot.durationMs.value,
      phase = MatchmakingRoomPhase.wireValue(snapshot.phase),
      finishedAt = snapshot.finishedAt.map(_.value),
      battleSession = snapshot.battleSession.map(BattleSessionDescriptorResponse.fromSession)
    )
}
