package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.replay.objects.ReplayId
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCommentsAPIMessage(
  replayId: Option[ReplayId],
  limit: Option[ReplayListLimitInput] = None
) extends APIMessageWithContext[ReplayService, ReplayCommentsResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCommentsResponse] =
    ReplayReadAPIPlanner.planComments(service, this)
}

object ReplayCommentsAPIMessage {
  import ReplayAPIMessageDecoding.given

  given Decoder[ReplayCommentsAPIMessage] =
    deriveDecoder[ReplayCommentsAPIMessage]
}
