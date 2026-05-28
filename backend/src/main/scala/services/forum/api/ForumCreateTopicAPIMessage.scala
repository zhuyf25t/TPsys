package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumCreateTopicAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    for
      command <- IO.fromEither(
        fields.toCreateTopicCommand.left.map(error =>
          ForumAPIMessageSupport.error(ForumApiErrorMapper.createErrorCode(error))
        )
      )
      topic <- IO.blocking(service.createTopic(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ForumAPIMessageSupport.error(ForumApiErrorMapper.createErrorCode(error)))
      }
    yield ForumTopicWrapperResponse.fromView(topic)
}

object ForumCreateTopicAPIMessage {
  given Decoder[ForumCreateTopicAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumCreateTopicAPIMessage(ForumRequestFields.fromApi(fields)))
}
