package slaydemo.backend.mail.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailId, MailRecord}
import slaydemo.backend.shared.database.AtomicFileWrite

final class FileMailRepository(storagePath: Path) extends MailRepository {
  private val lock = Object()
  private var recordsByKey: Map[String, MailRecord] = Map.empty

  loadFromDisk()

  override def listByOwner(owner: PlayerHandle): Vector[MailRecord] =
    lock.synchronized {
      recordsByKey.values.toVector
    }.filter(_.ownerHandle.key == owner.key)
      .sortWith(MailRepositoryOrderingRules.fileListOrder)

  override def save(record: MailRecord): MailRecord = {
    lock.synchronized {
      recordsByKey = recordsByKey.updated(recordKey(record.ownerHandle, record.id), record)
      persist()
    }
    record
  }

  override def markRead(owner: PlayerHandle, mailId: MailId): Option[MailRecord] =
    lock.synchronized {
      recordsByKey.get(recordKey(owner, mailId)).map { current =>
        val updated = current.markRead
        recordsByKey = recordsByKey.updated(recordKey(owner, mailId), updated)
        persist()
        updated
      }
    }

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          recordsByKey = MailFileJsonParser
            .parseRecords(raw)
            .map(record => recordKey(record.ownerHandle, record.id) -> record)
            .toMap
        }
      }
    }

  private def persist(): Unit = {
    val payload = MailFileJsonRenderer.renderPayload(
      recordsByKey.values.toVector.sortWith(MailRepositoryOrderingRules.filePersistenceOrder)
    )
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

  private def recordKey(owner: PlayerHandle, mailId: MailId): String =
    s"${owner.key}\u0000${mailId.value.trim}"
}

object FileMailRepository {
  def apply(storagePath: Path): FileMailRepository =
    new FileMailRepository(storagePath)
}
