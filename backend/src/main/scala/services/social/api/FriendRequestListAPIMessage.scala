package services.social.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestListAPIMessage(
  ownerHandle: Option[PlayerHandle]
) extends APIMessageWithContext[FriendRequestService, FriendRequestListResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestListResponse] =
    SocialAPIPlanner.planList(service, this)
}

object FriendRequestListAPIMessage {
  import SocialAPIMessageDecoding.given

  given Decoder[FriendRequestListAPIMessage] =
    deriveDecoder[FriendRequestListAPIMessage]
}
