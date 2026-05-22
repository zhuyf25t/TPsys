package services.mail.database

import services.identity.objects.PlayerHandle
import services.mail.objects.{MailId, MailRecord}

final class InMemoryMailRepository extends MailRepository {
  private val lock = Object()
  private var recordsByOwner: Map[String, Vector[MailRecord]] = Map.empty

  override def listByOwner(owner: PlayerHandle): Vector[MailRecord] =
    lock.synchronized {
      recordsByOwner.getOrElse(owner.key, Vector.empty).sortBy(MailRepositoryOrderingRules.inMemoryListKey)
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
          val updated = ownerRecords(index).markRead
          recordsByOwner = recordsByOwner.updated(owner.key, ownerRecords.updated(index, updated))
          Some(updated)
      }
    }
}

object InMemoryMailRepository {
  def apply(): InMemoryMailRepository =
    new InMemoryMailRepository()
}
