package services.social.database

import java.util.UUID

import services.social.objects.FriendRequestId

private[database] trait FriendRequestIdGenerator {
  def nextId(): FriendRequestId
}

private[database] object RandomFriendRequestIdGenerator extends FriendRequestIdGenerator {
  override def nextId(): FriendRequestId =
    FriendRequestId(s"friend-${UUID.randomUUID().toString}")
}
