package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumSetTopicVoteAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(fields))
      command <- IO.fromEither(fields.toSetTopicVoteCommand(topicId).left.map(ForumAPIMessageSupport.voteCommandError))
      topic <- IO.blocking(service.setTopicVote(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ForumAPIMessageSupport.mutationError(error))
      }
    yield ForumTopicWrapperResponse.fromView(topic)
}

object ForumSetTopicVoteAPIMessage {
  given Decoder[ForumSetTopicVoteAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumSetTopicVoteAPIMessage(ForumRequestFields.fromApi(fields)))
}
