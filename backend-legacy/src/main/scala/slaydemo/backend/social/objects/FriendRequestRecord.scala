package slaydemo.backend.social.objects

final case class FriendRequestRecord(
  id: String,
  sourceHandle: String,
  targetHandle: String,
  createdAt: Long,
  status: String = "pending",
  respondedAt: Option[Long] = None
)
