package slaydemo.backend.replay.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.ReplayId

private[routes] object ReplayRouteTargetParsers {
  def parseTarget(path: String): ReplayTarget = {
    val segments = routePath(path)
      .stripPrefix("/")
      .stripSuffix("/")
      .split("/", -1)
      .toVector
      .filter(_.nonEmpty)
      .map(decode)

    segments match {
      case Vector("replay", "catalog") =>
        ReplayTarget.Collection
      case Vector("replay", "catalog", replayId) if replayId.nonEmpty =>
        ReplayCommandParsers.parseReplayId(replayId).map(ReplayTarget.Detail.apply).getOrElse(ReplayTarget.InvalidReplayId)
      case Vector("replay", "catalog", replayId, "comments") if replayId.nonEmpty =>
        ReplayCommandParsers.parseReplayId(replayId).map(ReplayTarget.Comments.apply).getOrElse(ReplayTarget.InvalidReplayId)
      case _ =>
        ReplayTarget.Invalid
    }
  }

  def limit(rawQuery: String, default: Int): Int =
    queryParams(rawQuery).get("limit").flatMap(_.toIntOption).getOrElse(default)

  def replayHandleFromQuery(rawQuery: String): Option[PlayerHandle] =
    queryParams(rawQuery).get("handle").flatMap(PlayerHandle.forLookup)

  private def queryParams(rawQuery: String): Map[String, String] =
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

  private def routePath(path: String): String = {
    val raw = Option(path).getOrElse("")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private[routes] enum ReplayTarget {
  case Collection
  case Detail(replayId: ReplayId)
  case Comments(replayId: ReplayId)
  case Invalid
  case InvalidReplayId
}
