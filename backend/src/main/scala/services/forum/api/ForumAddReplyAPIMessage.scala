package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumAddReplyAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(fields))
      command <- IO.fromEither(fields.toAddReplyCommand(topicId).left.map(ForumAPIMessageSupport.mutationParseError))
      topic <- IO.blocking(service.addReply(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ForumAPIMessageSupport.mutationError(error))
      }
    yield ForumTopicWrapperResponse.fromView(topic)
}

object ForumAddReplyAPIMessage {
  given Decoder[ForumAddReplyAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumAddReplyAPIMessage(ForumRequestFields.fromApi(fields)))
}
