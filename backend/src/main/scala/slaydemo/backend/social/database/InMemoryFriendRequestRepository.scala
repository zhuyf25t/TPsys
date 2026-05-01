package slaydemo.backend.social.database

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord}

final class InMemoryFriendRequestRepository extends FriendRequestRepository {
  private val lock = Object()
  private var recordsById: Map[FriendRequestId, FriendRequestRecord] = Map.empty
  private var requestIdByPairKey: Map[String, FriendRequestId] = Map.empty
  private var nextRequestNumber: Long = 1L

  override def nextRequestId(): FriendRequestId =
    lock.synchronized {
      val id = FriendRequestId(f"friend-$nextRequestNumber%012d")
      nextRequestNumber += 1L
      id
    }

  override def findById(id: FriendRequestId): Option[FriendRequestRecord] =
    lock.synchronized {
      recordsById.get(id)
    }

  override def findByHandles(source: PlayerHandle, target: PlayerHandle): Option[FriendRequestRecord] =
    lock.synchronized {
      requestIdByPairKey.get(friendPairKey(source, target)).flatMap(recordsById.get)
    }

  override def listByOwner(owner: PlayerHandle): Vector[FriendRequestRecord] =
    lock.synchronized {
      recordsById.values.toVector
    }.filter(record => record.sourceHandle.key == owner.key || record.targetHandle.key == owner.key)
      .sortWith(compareRecentFirst)

  override def createIfAbsent(record: FriendRequestRecord): FriendRequestStoreCreateResult =
    lock.synchronized {
      val pairKey = friendPairKey(record.sourceHandle, record.targetHandle)
      requestIdByPairKey.get(pairKey).flatMap(recordsById.get) match {
        case Some(existing) =>
          FriendRequestStoreCreateResult.AlreadyExists(existing)
        case None =>
          recordsById = recordsById.updated(record.id, record)
          requestIdByPairKey = requestIdByPairKey.updated(pairKey, record.id)
          FriendRequestStoreCreateResult.Created(record)
      }
    }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    lock.synchronized {
      recordsById = recordsById.updated(record.id, record)
      requestIdByPairKey = requestIdByPairKey.updated(friendPairKey(record.sourceHandle, record.targetHandle), record.id)
    }
    record
  }

  private def friendPairKey(source: PlayerHandle, target: PlayerHandle): String =
    s"${source.key}->${target.key}"

  private def compareRecentFirst(left: FriendRequestRecord, right: FriendRequestRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value < right.id.value
}

object InMemoryFriendRequestRepository {
  def apply(): InMemoryFriendRequestRepository =
    new InMemoryFriendRequestRepository()
}
