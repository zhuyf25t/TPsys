package services.social.api

object SocialRequestTarget {
  private val FriendRequestPaths: Set[String] =
    Set("/social/friend-requests", "/api/social/friend-requests")
  private val FriendRequestRespondPaths: Set[String] =
    Set("/social/friend-requests/respond", "/api/social/friend-requests/respond")

  def isFriendRequestPath(path: String): Boolean =
    FriendRequestPaths.contains(path)

  def isFriendRequestRespondPath(path: String): Boolean =
    FriendRequestRespondPaths.contains(path)
}
