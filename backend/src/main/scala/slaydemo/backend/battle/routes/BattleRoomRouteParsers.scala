package slaydemo.backend.battle.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.battle.api.RealtimeRoomHeartbeatRequest
import slaydemo.backend.battle.objects.{BattleId, RoomId, TicketId}
import slaydemo.backend.battle.services.RealtimeRoomHeartbeatCommand
import slaydemo.backend.identity.objects.PlayerHandle

private[routes] object BattleRoomRouteParsers {
  def routePath(path: String): String = {
    val raw = Option(path).getOrElse("")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  def queryParams(rawQuery: String): Map[String, String] =
    Option(rawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  def battleIdFromStatePath(path: String): Option[BattleId] = {
    val normalized = routePath(path)
    val prefix = "/battle/state/"
    if normalized.startsWith(prefix) && normalized.length > prefix.length then
      nonEmptyText(decode(normalized.substring(prefix.length))).map(BattleId.apply)
    else None
  }

  def snapshotTarget(path: String, rawQuery: String): BattleRoomSnapshotTarget = {
    val normalized = routePath(path)
    roomIdFromSnapshotPath(normalized)
      .orElse(queryParams(rawQuery).get("roomId").flatMap(nonEmptyText).map(RoomId.apply)) match {
      case Some(roomId) =>
        BattleRoomSnapshotTarget.Room(roomId)
      case None if normalized == "/battle/rooms/snapshot" || normalized.endsWith("/snapshot") =>
        BattleRoomSnapshotTarget.MissingRoomId
      case None =>
        BattleRoomSnapshotTarget.RouteNotFound
    }
  }

  def heartbeatRoute(path: String): Option[Option[RoomId]] = {
    val normalized = routePath(path)
    val pathRoomId = roomIdFromHeartbeatPath(normalized)
    Option.when(normalized == "/battle/rooms/heartbeat" || pathRoomId.isDefined)(pathRoomId)
  }

  def heartbeatCommand(
    pathRoomId: Option[RoomId],
    rawQuery: String,
    fields: Map[String, BattleJsonValue]
  ): RealtimeRoomHeartbeatCommand = {
    val query = queryParams(rawQuery)
    val bodyRequest = RealtimeRoomHeartbeatRequest(
      roomId = readString(fields, "roomId"),
      ticketId = readString(fields, "ticketId"),
      handle = readString(fields, "handle")
    )

    RealtimeRoomHeartbeatCommand(
      roomId = pathRoomId
        .orElse(bodyRequest.roomId.flatMap(nonEmptyText).map(RoomId.apply))
        .orElse(query.get("roomId").flatMap(nonEmptyText).map(RoomId.apply)),
      ticketId = bodyRequest.ticketId
        .flatMap(nonEmptyText)
        .map(TicketId.apply)
        .orElse(query.get("ticketId").flatMap(nonEmptyText).map(TicketId.apply)),
      handle = bodyRequest.handle
        .flatMap(nonEmptyText)
        .orElse(query.get("handle").flatMap(nonEmptyText))
        .flatMap(PlayerHandle.forLookup)
    )
  }

  private def roomIdFromSnapshotPath(path: String): Option[RoomId] =
    roomIdFromRoomPath(path, "snapshot")

  private def roomIdFromHeartbeatPath(path: String): Option[RoomId] =
    roomIdFromRoomPath(path, "heartbeat")

  private def roomIdFromRoomPath(path: String, terminal: String): Option[RoomId] = {
    val prefix = "/battle/rooms/"
    if !path.startsWith(prefix) then None
    else
      path.stripPrefix(prefix).split("/", -1).toList match {
        case roomId :: action :: Nil if action == terminal && roomId.nonEmpty && roomId != "snapshot" && roomId != "heartbeat" =>
          Some(RoomId(decode(roomId)))
        case _ =>
          None
      }
  }

  private def readString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case Some(BattleJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(BattleJsonValue.NumberValue(value)) => Some(value.toString)
      case _                                        => None
    }

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private[routes] enum BattleRoomSnapshotTarget {
  case Room(roomId: RoomId)
  case MissingRoomId
  case RouteNotFound
}
