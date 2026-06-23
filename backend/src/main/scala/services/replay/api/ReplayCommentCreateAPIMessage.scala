package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.replay.objects.ReplayId
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCommentCreateAPIMessage(
  replayId: Option[ReplayId] = None,
  authorHandle: Option[ReplayCommentAuthorInput] = None,
  body: Option[ReplayCommentBodyInput] = None
) extends APIMessageWithContext[ReplayService, ReplayCommentWrapperResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCommentWrapperResponse] =
    ReplayCommentCreateAPIPlanner.plan(service, this)

  def withReplayId(replayId: ReplayId): ReplayCommentCreateAPIMessage =
    copy(replayId = Some(replayId))
}

object ReplayCommentCreateAPIMessage {
  import ReplayAPIMessageDecoding.given

  given Decoder[ReplayCommentCreateAPIMessage] =
    deriveDecoder[ReplayCommentCreateAPIMessage]
}
