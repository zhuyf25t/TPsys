package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.replay.objects.apiTypes.{ReplayCommentResponse, ReplayCommentsResponse}
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCommentsAPIMessage(
  replayId: String,
  limit: Option[Int] = None
) extends APIMessageWithContext[ReplayService, ReplayCommentsResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCommentsResponse] =
    for {
      parsedReplayId <- IO.fromEither(ReplayApiCodec.parseReplayId(replayId).left.map(ReplayAPIMessageSupport.recordDecodeError))
      _ <- service.load(parsedReplayId).flatMap {
        case Some(value) =>
          IO.pure(value)
        case None =>
          IO.raiseError(ReplayAPIMessageSupport.error(ReplayApiErrorCode.ReplayNotFound))
      }
      records <- service.listComments(parsedReplayId, limit.getOrElse(25))
    } yield ReplayCommentsResponse(records.map(ReplayCommentResponse.fromRecord))
}

object ReplayCommentsAPIMessage {
  given Decoder[ReplayCommentsAPIMessage] = deriveDecoder
}
