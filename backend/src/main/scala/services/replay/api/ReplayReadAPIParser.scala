package services.replay.api

import services.identity.objects.PlayerHandle
import services.replay.objects.ReplayId

object ReplayReadAPIParser {
  def catalogMessageFromQuery(query: Map[String, String]): ReplayCatalogAPIMessage =
    ReplayCatalogAPIMessage(
      limit = Some(ReplayListLimitInput.fromWire(query.get("limit").flatMap(_.toIntOption))),
      handle = ReplaySelectedHandleInput.fromWire(query.get("handle"))
    )

  def detailMessageFromPathAndQuery(replayId: ReplayId, query: Map[String, String]): ReplayDetailAPIMessage =
    ReplayDetailAPIMessage(
      replayId = Some(replayId),
      handle = ReplaySelectedHandleInput.fromWire(query.get("handle"))
    )

  def commentsMessageFromPathAndQuery(replayId: ReplayId, query: Map[String, String]): ReplayCommentsAPIMessage =
    ReplayCommentsAPIMessage(
      replayId = Some(replayId),
      limit = Some(ReplayListLimitInput.fromWire(query.get("limit").flatMap(_.toIntOption)))
    )

  def selectedHandle(message: ReplayCatalogAPIMessage): Option[PlayerHandle] =
    message.handle

  def selectedHandle(message: ReplayDetailAPIMessage): Option[PlayerHandle] =
    message.handle

  def listLimit(value: Option[ReplayListLimitInput]): ReplayListLimitInput =
    value.getOrElse(ReplayListLimitInput.Default)
}
