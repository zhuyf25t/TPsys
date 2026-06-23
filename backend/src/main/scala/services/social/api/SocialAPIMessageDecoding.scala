package services.social.api

import io.circe.Decoder

import services.identity.objects.PlayerHandle
import services.social.objects.{FriendRequestDecision, FriendRequestId}
import system.policies.HandlePolicy

private[api] object SocialAPIMessageDecoding {
  given optionalPlayerHandleDecoder: Decoder[Option[PlayerHandle]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(playerHandleFromWire))

  given optionalFriendRequestIdDecoder: Decoder[Option[FriendRequestId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(FriendRequestId.apply))

  given optionalDecisionDecoder: Decoder[Option[FriendRequestDecision]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(FriendRequestDecision.fromWire))

  def playerHandleFromWire(value: String): Option[PlayerHandle] =
    nonEmpty(value).map(PlayerHandle.apply)

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(HandlePolicy.trim).filter(_.nonEmpty)
}
