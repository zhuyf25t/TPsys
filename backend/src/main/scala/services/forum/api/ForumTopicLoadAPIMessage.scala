package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.forum.objects.ForumTopicId
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumTopicLoadAPIMessage(
  topicId: Option[ForumTopicId],
  viewerHandle: Option[ForumViewerHandleInput],
  viewer: Option[ForumViewerHandleInput],
  author: Option[ForumViewerHandleInput]
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  def selectedViewer: Option[PlayerHandle] =
    viewerHandle.orElse(viewer).orElse(author).flatMap(_.value)

  def withTopicId(pathTopicId: Option[ForumTopicId]): ForumTopicLoadAPIMessage =
    copy(topicId = pathTopicId.orElse(topicId))

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    ForumAPIPlanner.planTopicLoad(service, this)
}

object ForumTopicLoadAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumTopicLoadAPIMessage] =
    deriveDecoder[ForumTopicLoadAPIMessage]
}
