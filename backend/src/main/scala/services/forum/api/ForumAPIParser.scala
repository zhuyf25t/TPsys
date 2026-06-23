package services.forum.api

import services.forum.objects.ForumTopicId

object ForumAPIParser {
  def listMessageFromQuery(query: Map[String, String]): ForumTopicListAPIMessage =
    ForumTopicListAPIMessage(
      viewerHandle = None,
      viewer = viewerHandleFromQuery(query),
      author = None
    )

  def loadMessageFromPathAndQuery(path: String, query: Map[String, String]): ForumTopicLoadAPIMessage =
    ForumTopicLoadAPIMessage(
      topicId = ForumApiTargetParsers.topicIdFrom(path),
      viewerHandle = None,
      viewer = viewerHandleFromQuery(query),
      author = None
    )

  private def viewerHandleFromQuery(query: Map[String, String]): Option[ForumViewerHandleInput] =
    query.get("viewer").orElse(query.get("author")).map(value => ForumViewerHandleInput.fromWire(Some(value)))
}
