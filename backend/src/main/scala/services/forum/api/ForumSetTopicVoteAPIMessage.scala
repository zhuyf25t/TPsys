package services.forum.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.forum.objects.ForumTopicId
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumSetTopicVoteAPIMessage(
  topicId: Option[ForumTopicId],
  authorHandle: Option[ForumAuthorInput],
  author: Option[ForumAuthorInput],
  vote: Option[ForumVoteInput]
) extends APIMessageWithContext[ForumService, ForumTopicWrapperResponse] {
  def selectedAuthor: ForumAuthorInput =
    authorHandle.orElse(author).getOrElse(ForumAuthorInput.Invalid)

  def selectedVote: ForumVoteInput =
    vote.getOrElse(ForumVoteInput.Cleared)

  def withTopicId(pathTopicId: Option[ForumTopicId]): ForumSetTopicVoteAPIMessage =
    copy(topicId = pathTopicId.orElse(topicId))

  override def plan(service: ForumService, connection: Connection): IO[ForumTopicWrapperResponse] =
    ForumAPIPlanner.planSetTopicVote(service, this)
}

object ForumSetTopicVoteAPIMessage {
  import ForumAPIMessageDecoding.given

  given Decoder[ForumSetTopicVoteAPIMessage] =
    deriveDecoder[ForumSetTopicVoteAPIMessage]
}
