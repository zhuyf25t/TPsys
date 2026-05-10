package slaydemo.backend.mail.database

import slaydemo.backend.mail.objects.MailRecord

private[database] object MailRepositoryOrderingRules {
  def inMemoryListKey(record: MailRecord): Long =
    -record.createdAt.value

  def fileListOrder(left: MailRecord, right: MailRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value > right.id.value

  def filePersistenceOrder(left: MailRecord, right: MailRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value < right.id.value
}
