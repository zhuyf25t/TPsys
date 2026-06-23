package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.replay.objects.ReplayId
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayDetailAPIMessage(
  replayId: Option[ReplayId],
  handle: Option[PlayerHandle] = None
) extends APIMessageWithContext[ReplayService, ReplayDetailResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayDetailResponse] =
    ReplayReadAPIPlanner.planDetail(service, this)
}

object ReplayDetailAPIMessage {
  import ReplayAPIMessageDecoding.given

  given Decoder[ReplayDetailAPIMessage] =
    deriveDecoder[ReplayDetailAPIMessage]
}
