package slaydemo.backend.social.database

import java.util.UUID

import slaydemo.backend.social.objects.FriendRequestId

private[database] trait FriendRequestIdGenerator {
  def nextId(): FriendRequestId
}

private[database] object RandomFriendRequestIdGenerator extends FriendRequestIdGenerator {
  override def nextId(): FriendRequestId =
    FriendRequestId(s"friend-${UUID.randomUUID().toString}")
}
