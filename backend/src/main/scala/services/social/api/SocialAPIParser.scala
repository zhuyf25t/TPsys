package services.social.api

object SocialAPIParser {
  def listMessageFromQuery(query: Map[String, String]): FriendRequestListAPIMessage =
    FriendRequestListAPIMessage(
      ownerHandle = query.get("ownerHandle").flatMap(SocialAPIMessageDecoding.playerHandleFromWire)
    )
}
