package services.social.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.social.objects.apiTypes.{FriendRequestCreateApiRequest, FriendRequestCreateResponse}
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestCreateAPIMessage(
  request: FriendRequestCreateApiRequest
) extends APIMessageWithContext[FriendRequestService, FriendRequestCreateResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestCreateResponse] =
    for
      command <- IO.fromEither(
        SocialCommandParsers.parseCreateHandles(request).left.map(error =>
          SocialAPIMessageSupport.error(SocialApiErrorCode.fromCreateRouteError(error))
        )
      )
      result <- service.create(command.sourceHandle, command.targetHandle).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(SocialAPIMessageSupport.error(SocialApiErrorCode.fromCreateServiceError(error)))
      }
    yield FriendRequestCreateResponse.fromResult(result)
}

object FriendRequestCreateAPIMessage {
  given Decoder[FriendRequestCreateAPIMessage] =
    Decoder[FriendRequestCreateApiRequest].map(FriendRequestCreateAPIMessage.apply)
}
