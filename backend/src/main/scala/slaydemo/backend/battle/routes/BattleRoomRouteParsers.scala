package slaydemo.backend.battle.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.battle.objects.BattleId

private[routes] object BattleRoomRouteParsers {
  def routePath(path: String): String = {
    val raw = Option(path).getOrElse("")
    raw match {
      case "/api"                         => "/"
      case "/api/battlestatereadapi"      => "/battle/state"
      case "/api/battlestatestreamapi"    => "/battle/state/stream"
      case value if value.startsWith("/api/") => value.stripPrefix("/api")
      case value                          => value
    }
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

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}
