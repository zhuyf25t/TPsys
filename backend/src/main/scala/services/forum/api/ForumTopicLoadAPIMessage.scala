package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumTopicLoadAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(fields))
      topic <- IO.blocking(service.loadTopic(topicId, ForumAPIMessageSupport.viewerHandle(fields))).flatMap {
        case Some(value) =>
          IO.pure(value)
        case None =>
          IO.raiseError(ForumAPIMessageSupport.error(ForumApiErrorCode.TopicNotFound))
      }
    yield ForumTopicWrapperResponse.fromView(topic)
}

object ForumTopicLoadAPIMessage {
  given Decoder[ForumTopicLoadAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumTopicLoadAPIMessage(ForumRequestFields.fromApi(fields)))
}
