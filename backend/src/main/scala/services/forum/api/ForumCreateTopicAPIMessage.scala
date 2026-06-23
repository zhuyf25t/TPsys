package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.forum.objects.{ForumBody, ForumTag, ForumTitle}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumCreateTopicAPIMessage(
  title: ForumTitle,
  body: ForumBody,
  tag: ForumTag,
  authorHandle: Option[ForumAuthorInput],
  author: Option[ForumAuthorInput]
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  def selectedAuthor: ForumAuthorInput =
    authorHandle.orElse(author).getOrElse(ForumAuthorInput.Invalid)

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    ForumAPIPlanner.planCreateTopic(service, this)
}

object ForumCreateTopicAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumCreateTopicAPIMessage] =
    deriveDecoder[ForumCreateTopicAPIMessage]
}
