package slaydemo.backend.mail.database

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailId, MailRecord}

final class InMemoryMailRepository extends MailRepository {
  private val lock = Object()
  private var recordsByOwner: Map[String, Vector[MailRecord]] = Map.empty

  override def listByOwner(owner: PlayerHandle): Vector[MailRecord] =
    lock.synchronized {
      recordsByOwner.getOrElse(owner.key, Vector.empty).sortBy(mail => -mail.createdAt.value)
    }

  override def save(record: MailRecord): MailRecord = {
    lock.synchronized {
      val ownerRecords = recordsByOwner.getOrElse(record.ownerHandle.key, Vector.empty)
      val nextRecords = ownerRecords.indexWhere(_.id == record.id) match {
        case -1    => ownerRecords :+ record
        case index => ownerRecords.updated(index, record)
      }
      recordsByOwner = recordsByOwner.updated(record.ownerHandle.key, nextRecords)
    }
    record
  }

  override def markRead(owner: PlayerHandle, mailId: MailId): Option[MailRecord] =
    lock.synchronized {
      val ownerRecords = recordsByOwner.getOrElse(owner.key, Vector.empty)
      ownerRecords.indexWhere(_.id == mailId) match {
        case -1 =>
          None
        case index =>
          val updated = ownerRecords(index).copy(unread = false)
          recordsByOwner = recordsByOwner.updated(owner.key, ownerRecords.updated(index, updated))
          Some(updated)
      }
    }
}

object InMemoryMailRepository {
  def apply(): InMemoryMailRepository =
    new InMemoryMailRepository()
}
