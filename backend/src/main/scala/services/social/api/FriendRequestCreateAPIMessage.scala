package services.social.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestCreateAPIMessage(
  sourceHandle: Option[PlayerHandle],
  targetHandle: Option[PlayerHandle]
) extends APIMessageWithContext[FriendRequestService, FriendRequestCreateResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestCreateResponse] =
    SocialAPIPlanner.planCreate(service, this)
}

object FriendRequestCreateAPIMessage {
  import SocialAPIMessageDecoding.given

  given Decoder[FriendRequestCreateAPIMessage] =
    deriveDecoder[FriendRequestCreateAPIMessage]
}
