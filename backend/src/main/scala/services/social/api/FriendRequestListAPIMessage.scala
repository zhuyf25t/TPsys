package services.social.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.social.objects.apiTypes.{FriendRequestListApiRequest, FriendRequestListResponse}
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestListAPIMessage(
  request: FriendRequestListApiRequest
) extends APIMessageWithContext[FriendRequestService, FriendRequestListResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestListResponse] =
    for
      owner <- IO.fromEither(
        FriendRequestOwnerQuery.parse(request.ownerHandle).left.map(error =>
          SocialAPIMessageSupport.error(SocialApiErrorCode.fromOwnerError(error))
        )
      )
      records <- IO.blocking(service.list(owner))
    yield FriendRequestListResponse.fromRecords(records)
}

object FriendRequestListAPIMessage {
  given Decoder[FriendRequestListAPIMessage] =
    Decoder[FriendRequestListApiRequest].map(FriendRequestListAPIMessage.apply)
}
