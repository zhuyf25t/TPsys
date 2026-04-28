package slaydemo.backend.mails.services

import slaydemo.backend.mails.database.MailRepository
import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.shared.rules.HandleRules

final class DefaultMailService(repository: MailRepository) extends MailService {
  override def list(ownerHandle: String): Seq[MailRecord] = {
    val normalized = normalizeOwnerHandle(ownerHandle)
    if (!HandleRules.isPlayableIdentityHandle(normalized)) Seq.empty
    else repository.listByOwner(normalized)
  }

  override def create(record: MailRecord): MailRecord =
    if (HandleRules.isPlayableIdentityHandle(record.ownerHandle)) repository.save(record)
    else record

  override def markRead(ownerHandle: String, mailId: String): Boolean = {
    val normalized = normalizeOwnerHandle(ownerHandle)
    if (!HandleRules.isPlayableIdentityHandle(normalized)) false
    else repository.markRead(normalized, mailId.trim)
  }

  private def normalizeOwnerHandle(ownerHandle: String): String =
    Option(ownerHandle).getOrElse("").trim
}
