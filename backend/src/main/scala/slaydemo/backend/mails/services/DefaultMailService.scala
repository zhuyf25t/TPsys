package slaydemo.backend.mails.services

import slaydemo.backend.mails.database.MailRepository
import slaydemo.backend.mails.objects.MailRecord

final class DefaultMailService(repository: MailRepository) extends MailService {
  override def list(ownerHandle: String): Seq[MailRecord] = {
    val normalized = ownerHandle.trim
    if (normalized.isEmpty) Seq.empty
    else repository.listByOwner(normalized)
  }

  override def create(record: MailRecord): MailRecord = repository.save(record)

  override def markRead(ownerHandle: String, mailId: String): Boolean =
    repository.markRead(ownerHandle.trim, mailId.trim)
}
