package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.replay.objects.apiTypes.{ReplayDetailRecordResponse, ReplayDetailResponse}
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayDetailAPIMessage(
  replayId: String,
  handle: Option[String] = None
) extends APIMessageWithContext[ReplayService, ReplayDetailResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayDetailResponse] =
    for {
      parsedReplayId <- IO.fromEither(ReplayApiCodec.parseReplayId(replayId).left.map(ReplayAPIMessageSupport.recordDecodeError))
      record <- service.load(parsedReplayId).flatMap {
        case Some(value) =>
          IO.pure(value)
        case None =>
          IO.raiseError(ReplayAPIMessageSupport.error(ReplayApiErrorCode.ReplayNotFound))
      }
    } yield ReplayDetailResponse(
      ReplayDetailRecordResponse.fromRecord(record, selectedHandle)
    )

  private def selectedHandle: Option[PlayerHandle] =
    handle
      .flatMap(ReplayApiCodec.nonEmpty)
      .flatMap(PlayerHandle.forLookup)
}

object ReplayDetailAPIMessage {
  given Decoder[ReplayDetailAPIMessage] = deriveDecoder
}
