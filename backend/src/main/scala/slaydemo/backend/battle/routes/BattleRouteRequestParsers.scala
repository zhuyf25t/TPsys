package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.BattleId

private[routes] object BattleRouteRequestParsers {
  def isStateStreamPath(path: String): Boolean =
    BattleRoomRouteParsers.routePath(path) == "/battle/state/stream"

  def stateBattleId(path: String, rawQuery: String): Option[BattleId] =
    BattleRoomRouteParsers
      .battleIdFromStatePath(path)
      .orElse(BattleRoomRouteParsers.queryParams(rawQuery).get("battleId").flatMap(nonEmptyText).map(BattleId.apply))

  def stateStreamBattleId(rawQuery: String): Option[BattleId] =
    BattleRoomRouteParsers.queryParams(rawQuery).get("battleId").flatMap(nonEmptyText).map(BattleId.apply)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
