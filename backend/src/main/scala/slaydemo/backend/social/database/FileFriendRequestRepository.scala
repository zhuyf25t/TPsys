package slaydemo.backend.social.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.database.AtomicFileWrite
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord}

final class FileFriendRequestRepository(storagePath: Path) extends FriendRequestRepository {
  private val lock = Object()
  private var recordsById: Map[FriendRequestId, FriendRequestRecord] = Map.empty
  private var nextRequestNumber: Long = 1L

  loadFromDisk()

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
      recordsById.values
        .filter(record => record.sourceHandle.key == source.key && record.targetHandle.key == target.key)
        .toVector
        .sortWith(FriendRequestOrderingRules.pairCandidates)
        .headOption
    }

  override def listByOwner(owner: PlayerHandle): Vector[FriendRequestRecord] =
    lock.synchronized {
      recordsById.values.toVector
    }.filter(record => record.sourceHandle.key == owner.key || record.targetHandle.key == owner.key)
      .sortWith(FriendRequestOrderingRules.recentFirst)

  override def createIfAbsent(record: FriendRequestRecord): FriendRequestStoreCreateResult =
    lock.synchronized {
      findByHandles(record.sourceHandle, record.targetHandle) match {
        case Some(existing) =>
          FriendRequestStoreCreateResult.AlreadyExists(existing)
        case None =>
          recordsById = recordsById.updated(record.id, record)
          advanceNextRequestNumber(record.id)
          persist()
          FriendRequestStoreCreateResult.Created(record)
      }
    }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    lock.synchronized {
      recordsById = recordsById.updated(record.id, record)
      advanceNextRequestNumber(record.id)
      persist()
    }
    record
  }

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          recordsById = FriendRequestFileJsonParser
            .parseRecords(raw)
            .map(record => record.id -> record)
            .toMap
          recordsById.keys.foreach(advanceNextRequestNumber)
        }
      }
    }

  private def persist(): Unit = {
    val payload = FriendRequestFileJsonRenderer.renderPayload(recordsById.values.toVector.sortWith(FriendRequestOrderingRules.recentFirst))
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

  private def advanceNextRequestNumber(id: FriendRequestId): Unit =
    parseNumericRequestId(id).foreach { number =>
      nextRequestNumber = math.max(nextRequestNumber, number + 1L)
    }

  private def parseNumericRequestId(id: FriendRequestId): Option[Long] = {
    val prefix = "friend-"
    val value = id.value.trim
    Option
      .when(value.startsWith(prefix) && value.drop(prefix.length).forall(_.isDigit))(value.drop(prefix.length).toLong)
  }

}

object FileFriendRequestRepository {
  def apply(storagePath: Path): FileFriendRequestRepository =
    new FileFriendRequestRepository(storagePath)
}
