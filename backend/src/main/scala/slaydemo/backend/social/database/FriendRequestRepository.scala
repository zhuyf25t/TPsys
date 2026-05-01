package slaydemo.backend.social.database

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord}

enum FriendRequestStoreCreateResult {
  case Created(request: FriendRequestRecord)
  case AlreadyExists(request: FriendRequestRecord)
}

trait FriendRequestRepository {
  def nextRequestId(): FriendRequestId
  def findById(id: FriendRequestId): Option[FriendRequestRecord]
  def findByHandles(source: PlayerHandle, target: PlayerHandle): Option[FriendRequestRecord]
  def listByOwner(owner: PlayerHandle): Vector[FriendRequestRecord]
  def createIfAbsent(record: FriendRequestRecord): FriendRequestStoreCreateResult
  def save(record: FriendRequestRecord): FriendRequestRecord
}
