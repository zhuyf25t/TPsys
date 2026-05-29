package services.social.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.social.objects.apiTypes.{FriendRequestRespondApiRequest, FriendRequestRespondResponse}
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestRespondAPIMessage(
  request: FriendRequestRespondApiRequest
) extends APIMessageWithContext[FriendRequestService, FriendRequestRespondResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestRespondResponse] =
    for
      command <- IO.fromEither(
        SocialCommandParsers.parseRespondCommand(request).left.map(error =>
          SocialAPIMessageSupport.error(SocialApiErrorCode.fromRespondRouteError(error))
        )
      )
      result <- service.respond(command.requestId, command.actorHandle, command.decision).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(SocialAPIMessageSupport.error(SocialApiErrorCode.fromRespondServiceError(error)))
      }
    yield FriendRequestRespondResponse.fromResult(result)
}

object FriendRequestRespondAPIMessage {
  given Decoder[FriendRequestRespondAPIMessage] =
    Decoder[FriendRequestRespondApiRequest].map(FriendRequestRespondAPIMessage.apply)
}
