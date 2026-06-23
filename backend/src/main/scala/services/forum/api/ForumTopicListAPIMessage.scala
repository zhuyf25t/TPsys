package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumTopicListAPIMessage(
  viewerHandle: Option[ForumViewerHandleInput],
  viewer: Option[ForumViewerHandleInput],
  author: Option[ForumViewerHandleInput]
) extends APIMessageWithContext[ForumService, ForumTopicListResponse] {
  def selectedViewer: Option[PlayerHandle] =
    viewerHandle.orElse(viewer).orElse(author).flatMap(_.value)

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicListResponse] =
    ForumAPIPlanner.planTopicList(service, this)
}

object ForumTopicListAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumTopicListAPIMessage] =
    deriveDecoder[ForumTopicListAPIMessage]
}
