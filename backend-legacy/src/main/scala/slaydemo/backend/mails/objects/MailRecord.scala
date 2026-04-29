package slaydemo.backend.mails.objects

final case class MailRecord(
  id: String,
  ownerHandle: String,
  kind: String,
  subject: String,
  excerpt: String,
  senderLabel: String,
  unread: Boolean,
  important: Boolean,
  createdAt: Long
)
