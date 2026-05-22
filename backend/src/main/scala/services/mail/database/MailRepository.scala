package services.mail.database

import services.identity.objects.PlayerHandle
import services.mail.objects.{MailId, MailRecord}

trait MailRepository {
  def listByOwner(owner: PlayerHandle): Vector[MailRecord]
  def save(record: MailRecord): MailRecord
  def markRead(owner: PlayerHandle, mailId: MailId): Option[MailRecord]
}
