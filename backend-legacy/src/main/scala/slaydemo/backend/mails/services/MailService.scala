package slaydemo.backend.mails.services

import slaydemo.backend.mails.objects.MailRecord

trait MailService {
  def list(ownerHandle: String): Seq[MailRecord]
  def create(record: MailRecord): MailRecord
  def markRead(ownerHandle: String, mailId: String): Boolean
}
