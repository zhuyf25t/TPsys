package services.replay.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.replay.objects.apiTypes.{ReplayDetailRecordResponse, ReplayDetailResponse}
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayRecordAPIMessage(
  request: ReplayRecordAPIRequest
) extends APIMessageWithContext[ReplayService, ReplayDetailResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayDetailResponse] =
    for {
      command <- IO.fromEither(request.toCommand.left.map(ReplayAPIMessageSupport.recordDecodeError))
      record <- IO.blocking(service.record(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(ReplayAPIMessageSupport.recordServiceError(error))
      }
    } yield ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, None))
}

object ReplayRecordAPIMessage {
  given Decoder[ReplayRecordAPIMessage] =
    Decoder[ReplayRecordAPIRequest].map(ReplayRecordAPIMessage.apply)
}
