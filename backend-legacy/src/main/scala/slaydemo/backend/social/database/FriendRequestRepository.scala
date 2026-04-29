package slaydemo.backend.social.database

import slaydemo.backend.social.objects.FriendRequestRecord

trait FriendRequestRepository {
  def findById(id: String): Option[FriendRequestRecord]
  def findByHandles(sourceHandle: String, targetHandle: String): Option[FriendRequestRecord]
  def listByOwner(ownerHandle: String): Seq[FriendRequestRecord]
  def save(record: FriendRequestRecord): FriendRequestRecord
  def updateStatus(id: String, status: String, respondedAt: Long): Option[FriendRequestRecord]
}
