package services.social.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.social.objects.{FriendRequestDecision, FriendRequestId}
import services.social.services.FriendRequestService
import system.api.APIMessageWithContext

final case class FriendRequestRespondAPIMessage(
  requestId: Option[FriendRequestId],
  actorHandle: Option[PlayerHandle],
  decision: Option[FriendRequestDecision]
) extends APIMessageWithContext[FriendRequestService, FriendRequestRespondResponse] {
  override def plan(service: FriendRequestService, connection: Connection): IO[FriendRequestRespondResponse] =
    SocialAPIPlanner.planRespond(service, this)
}

object FriendRequestRespondAPIMessage {
  import SocialAPIMessageDecoding.given

  given Decoder[FriendRequestRespondAPIMessage] =
    deriveDecoder[FriendRequestRespondAPIMessage]
}
