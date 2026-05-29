package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumSetReplyVoteAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(fields))
      replyId <- IO.fromEither(ForumAPIMessageSupport.replyId(fields))
      command <- IO.fromEither(fields.toSetReplyVoteCommand(topicId, replyId).left.map(ForumAPIMessageSupport.voteCommandError))
      topic <- service.setReplyVote(command).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ForumAPIMessageSupport.mutationError(error))
      }
    yield ForumTopicWrapperResponse.fromView(topic)
}

object ForumSetReplyVoteAPIMessage {
  given Decoder[ForumSetReplyVoteAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumSetReplyVoteAPIMessage(ForumRequestFields.fromApi(fields)))
}
