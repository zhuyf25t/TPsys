package slaydemo.backend.governance.objects

final case class GovernanceReviewNotificationRecord(
  id: String,
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String,
  createdAt: Long,
  mailId: String
)
