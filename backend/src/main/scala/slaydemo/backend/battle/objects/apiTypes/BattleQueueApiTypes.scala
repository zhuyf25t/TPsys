package slaydemo.backend.battle.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleQueueJoinCommand, RealtimeRoomHeartbeatCommand}
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

enum BattleQueueJoinAPIRequestError {
  case InvalidJsonObject
  case InvalidRating(message: String)
  case InvalidHandle
  case MissingSession
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
  def toTicketId: Either[String, TicketId] =
    ticketId.flatMap(nonEmptyText).map(TicketId.apply).toRight("ticketId is required.")

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

object BattleQueueLeaveAPIRequest {
  given Decoder[BattleQueueLeaveAPIRequest] = (cursor: HCursor) =>
    BattleQueueJoinAPIRequest.optionalText(cursor, "ticketId").map(BattleQueueLeaveAPIRequest.apply)

  def decodeTicketId(json: Json): Either[String, TicketId] =
    json.as[BattleQueueLeaveAPIRequest]
      .left.map(_ => BattleQueueAPIRequestErrors.InvalidJsonObjectMessage)
      .flatMap(_.toTicketId)
}

final case class BattleQueueLeaveAPIResponse(left: Boolean)

object BattleQueueLeaveAPIResponse {
  given Encoder[BattleQueueLeaveAPIResponse] = deriveEncoder
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
  ): Either[String, RealtimeRoomHeartbeatCommand] =
    json.as[RealtimeRoomHeartbeatAPIRequest]
      .left.map(_ => BattleQueueAPIRequestErrors.InvalidJsonObjectMessage)
      .map(_.toCommand(pathRoomId, query))
}

object RealtimeRoomRequestTarget {
  def hasSnapshotPathRoomId(path: String): Boolean =
    roomIdFromPath(path, "snapshot").isDefined

  def hasHeartbeatPathRoomId(path: String): Boolean =
    roomIdFromPath(path, "heartbeat").isDefined

  def roomIdFromSnapshot(path: String, query: Map[String, String]): Option[RoomId] =
    roomIdFromPath(path, "snapshot")
      .orElse(roomIdFromQuery(query))

  def roomIdFromHeartbeatPath(path: String): Option[RoomId] =
    roomIdFromPath(path, "heartbeat")

  private def roomIdFromPath(path: String, terminal: String): Option[RoomId] = {
    val normalized = path.stripPrefix("/api")
    val prefix = "/battle/rooms/"
    if !normalized.startsWith(prefix) then None
    else
      normalized.stripPrefix(prefix).split("/", -1).toList match {
        case roomId :: action :: Nil if action == terminal && roomId.nonEmpty && roomId != "snapshot" && roomId != "heartbeat" =>
          Some(RoomId(roomId))
        case _ =>
          None
      }
  }

  private def roomIdFromQuery(query: Map[String, String]): Option[RoomId] =
    query.get("roomId").flatMap(nonEmptyText).map(RoomId.apply)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
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
      .left.map(error => BattleQueueJoinAPIRequestError.InvalidRating(error.message))
      .flatMap(_.toCommand)
}

private object BattleQueueAPIRequestErrors {
  val InvalidJsonObjectMessage: String =
    "Request body must be a JSON object with supported primitive or object fields."
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
    Encoder.instance { value =>
      Json.obj(
        optionalResponseFields(
          Vector(
            "playerId" -> Json.fromString(value.playerId),
            "handle" -> Json.fromString(value.handle),
            "joinedAt" -> Json.fromLong(value.joinedAt),
            "lastSeen" -> Json.fromLong(value.lastSeen)
          ),
          "rating" -> value.rating.map(Json.fromInt),
          "avatar" -> value.avatar.map(Json.fromString),
          "skin" -> value.skin.map(Json.fromString)
        )*
      )
    }

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
    Encoder.instance { value =>
      Json.obj(
        optionalResponseFields(
          Vector(
            "seat" -> Json.fromInt(value.seat),
            "playerId" -> Json.fromString(value.playerId),
            "handle" -> Json.fromString(value.handle),
            "joinedAt" -> Json.fromLong(value.joinedAt)
          ),
          "rating" -> value.rating.map(Json.fromInt),
          "avatar" -> value.avatar.map(Json.fromString),
          "skin" -> value.skin.map(Json.fromString)
        )*
      )
    }

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
    Encoder.instance { value =>
      Json.obj(
        optionalResponseFields(
          Vector(
            "seat" -> Json.fromInt(value.seat),
            "playerId" -> Json.fromString(value.playerId),
            "heroId" -> Json.fromString(value.heroId),
            "handle" -> Json.fromString(value.handle),
            "displayName" -> Json.fromString(value.displayName),
            "joinedAt" -> Json.fromLong(value.joinedAt),
            "isBot" -> Json.fromBoolean(value.isBot),
            "spawnPointIndex" -> Json.fromInt(value.spawnPointIndex)
          ),
          "rating" -> value.rating.map(Json.fromInt),
          "avatar" -> value.avatar.map(Json.fromString),
          "skin" -> value.skin.map(Json.fromString)
        )*
      )
    }

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

private def optionalResponseFields(
  requiredFields: Vector[(String, Json)],
  optionalFields: (String, Option[Json])*
): Vector[(String, Json)] =
  requiredFields ++ optionalFields.toVector.flatMap { case (key, value) => value.map(key -> _) }
