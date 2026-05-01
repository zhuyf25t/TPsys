package slaydemo.backend.mail.database

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailId, MailRecord}

trait MailRepository {
  def listByOwner(owner: PlayerHandle): Vector[MailRecord]
  def save(record: MailRecord): MailRecord
  def markRead(owner: PlayerHandle, mailId: MailId): Option[MailRecord]
}
