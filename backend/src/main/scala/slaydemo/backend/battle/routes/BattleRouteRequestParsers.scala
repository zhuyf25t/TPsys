package slaydemo.backend.battle.routes

import slaydemo.backend.battle.api.BattleQueueLeaveRequest
import slaydemo.backend.battle.objects.{BattleId, TicketId}

private[routes] object BattleRouteRequestParsers {
  def isStateStreamPath(path: String): Boolean =
    BattleRoomRouteParsers.routePath(path) == "/battle/state/stream"

  def statusTicketId(rawQuery: String): Option[TicketId] =
    BattleRoomRouteParsers.queryParams(rawQuery).get("ticketId").flatMap(nonEmptyText).map(TicketId.apply)

  def parseLeaveRequest(fields: Map[String, BattleJsonValue]): Either[String, BattleQueueLeaveRequest] =
    readString(fields, "ticketId").flatMap(nonEmptyText) match {
      case Some(ticketId) => Right(BattleQueueLeaveRequest(ticketId))
      case None           => Left("ticketId is required.")
    }

  def stateBattleId(path: String, rawQuery: String): Option[BattleId] =
    BattleRoomRouteParsers
      .battleIdFromStatePath(path)
      .orElse(BattleRoomRouteParsers.queryParams(rawQuery).get("battleId").flatMap(nonEmptyText).map(BattleId.apply))

  def stateStreamBattleId(rawQuery: String): Option[BattleId] =
    BattleRoomRouteParsers.queryParams(rawQuery).get("battleId").flatMap(nonEmptyText).map(BattleId.apply)

  private def readString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case Some(BattleJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(BattleJsonValue.NumberValue(value)) => Some(value.toString)
      case _                                        => None
    }

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
