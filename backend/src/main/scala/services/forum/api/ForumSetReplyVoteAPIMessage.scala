package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.forum.objects.{ForumReplyId, ForumTopicId}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumSetReplyVoteAPIMessage(
  topicId: Option[ForumTopicId],
  replyId: Option[ForumReplyId],
  authorHandle: Option[ForumAuthorInput],
  author: Option[ForumAuthorInput],
  vote: Option[ForumVoteInput]
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  def selectedAuthor: ForumAuthorInput =
    authorHandle.orElse(author).getOrElse(ForumAuthorInput.Invalid)

  def selectedVote: ForumVoteInput =
    vote.getOrElse(ForumVoteInput.Cleared)

  def withPathIds(pathTopicId: Option[ForumTopicId], pathReplyId: Option[ForumReplyId]): ForumSetReplyVoteAPIMessage =
    copy(
      topicId = pathTopicId.orElse(topicId),
      replyId = pathReplyId.orElse(replyId)
    )

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    ForumAPIPlanner.planSetReplyVote(service, this)
}

object ForumSetReplyVoteAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumSetReplyVoteAPIMessage] =
    deriveDecoder[ForumSetReplyVoteAPIMessage]
}
