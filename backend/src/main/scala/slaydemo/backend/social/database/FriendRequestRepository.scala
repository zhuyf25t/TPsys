package slaydemo.backend.social.database

import slaydemo.backend.social.objects.FriendRequestRecord

trait FriendRequestRepository {
  def findByHandles(sourceHandle: String, targetHandle: String): Option[FriendRequestRecord]
  def save(record: FriendRequestRecord): FriendRequestRecord
}
