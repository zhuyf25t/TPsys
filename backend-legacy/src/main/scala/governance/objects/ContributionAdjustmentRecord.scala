package slaydemo.backend.governance.objects

final case class ContributionAdjustmentRecord(
  id: String,
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  createdAt: Long,
  sourceLabel: String = "",
  sourcePath: String = ""
)
