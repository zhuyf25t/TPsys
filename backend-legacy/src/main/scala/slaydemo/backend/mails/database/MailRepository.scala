package slaydemo.backend.mails.database

import slaydemo.backend.mails.objects.MailRecord

trait MailRepository {
  def listByOwner(ownerHandle: String): Seq[MailRecord]
  def save(record: MailRecord): MailRecord
  def markRead(ownerHandle: String, mailId: String): Boolean
}
