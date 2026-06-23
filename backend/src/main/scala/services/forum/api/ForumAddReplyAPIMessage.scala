package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.forum.objects.{ForumBody, ForumTopicId}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumAddReplyAPIMessage(
  topicId: Option[ForumTopicId],
  body: ForumBody,
  authorHandle: Option[ForumAuthorInput],
  author: Option[ForumAuthorInput]
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  def selectedAuthor: ForumAuthorInput =
    authorHandle.orElse(author).getOrElse(ForumAuthorInput.Invalid)

  def withTopicId(pathTopicId: Option[ForumTopicId]): ForumAddReplyAPIMessage =
    copy(topicId = pathTopicId.orElse(topicId))

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    ForumAPIPlanner.planAddReply(service, this)
}

object ForumAddReplyAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumAddReplyAPIMessage] =
    deriveDecoder[ForumAddReplyAPIMessage]
}
