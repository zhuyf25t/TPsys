package services.replay.api

import io.circe.Decoder

import services.identity.objects.PlayerHandle
import services.replay.objects.ReplayId
import services.replay.services.ReplayIdentifierPolicy

enum ReplayRecordDecodeError {
  case BadJsonObject
  case InvalidReplayId
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

enum ReplayCommentDecodeError {
  case BadJsonObject
  case InvalidReplayId
  case InvalidAuthorHandle
  case VisitorNotAllowed
}

enum ReplayCatalogTarget {
  case Collection
  case Detail(replayId: ReplayId)
  case Comments(replayId: ReplayId)
  case InvalidReplayId
}

final case class ReplayCatalogQuery(
  limit: Int,
  selectedHandle: Option[PlayerHandle]
)

object ReplayApiCodec {
  private val CatalogBasePaths: Vector[String] =
    Vector("/replay/catalog", "/api/replay/catalog")

  def catalogTarget(path: String): Option[ReplayCatalogTarget] =
    CatalogBasePaths.collectFirst(Function.unlift(basePathTarget(path)))

  def selectedHandle(query: Map[String, String]): Option[PlayerHandle] =
    ReplaySelectedHandleInput.fromWire(query.get("handle"))

  def limit(query: Map[String, String]): Int =
    ReplayListLimitInput.fromWire(query.get("limit").flatMap(_.toIntOption)).value

  def catalogQuery(query: Map[String, String]): ReplayCatalogQuery =
    ReplayCatalogQuery(
      limit = limit(query),
      selectedHandle = selectedHandle(query)
    )

  private[api] given Decoder[ReplayId] =
    Decoder.decodeString.emap(value =>
      parseReplayIdValue(value).map(Right.apply).getOrElse(Left("invalid_replay_id"))
    )

  private def basePathTarget(path: String)(basePath: String): Option[ReplayCatalogTarget] =
    if path == basePath then Some(ReplayCatalogTarget.Collection)
    else if path.startsWith(s"$basePath/") then {
      val suffix = path.stripPrefix(s"$basePath/")
      if suffix.endsWith("/comments") then
        parseReplayIdValue(suffix.stripSuffix("/comments"))
          .map(ReplayCatalogTarget.Comments.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
      else
        parseReplayIdValue(suffix)
          .map(ReplayCatalogTarget.Detail.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
    } else None

  private[api] def replayIdFromWire(value: String): Option[ReplayId] =
    parseReplayIdValue(value)

  private[api] def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def parseReplayIdValue(value: String): Option[ReplayId] =
    nonEmpty(value).filter(ReplayIdentifierPolicy.isSafeIdentifier).map(ReplayId.apply)
}
