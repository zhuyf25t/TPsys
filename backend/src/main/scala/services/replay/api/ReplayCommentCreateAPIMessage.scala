package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.replay.objects.apiTypes.{ReplayCommentResponse, ReplayCommentWrapperResponse}
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCommentCreateAPIMessage(
  replayId: String,
  authorHandle: Option[String] = None,
  body: Option[String] = None
) extends APIMessageWithContext[ReplayService, ReplayCommentWrapperResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCommentWrapperResponse] =
    for {
      parsedReplayId <- IO.fromEither(ReplayApiCodec.parseReplayId(replayId).left.map(ReplayAPIMessageSupport.recordDecodeError))
      _ <- IO.blocking(service.load(parsedReplayId)).flatMap {
        case Some(value) =>
          IO.pure(value)
        case None =>
          IO.raiseError(ReplayAPIMessageSupport.error(ReplayApiErrorCode.ReplayNotFound))
      }
      command <- IO.fromEither(
        ReplayCommentAPIRequest(authorHandle = authorHandle, body = body)
          .toCommand(parsedReplayId)
          .left
          .map(ReplayAPIMessageSupport.commentDecodeError)
      )
      comment <- IO.blocking(service.addComment(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ReplayAPIMessageSupport.commentServiceError(error))
      }
    } yield ReplayCommentWrapperResponse(ReplayCommentResponse.fromRecord(comment))
}

object ReplayCommentCreateAPIMessage {
  given Decoder[ReplayCommentCreateAPIMessage] = deriveDecoder
}
