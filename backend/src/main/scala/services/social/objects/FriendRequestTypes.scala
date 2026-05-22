package services.social.objects

import java.util.Locale

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle

final case class FriendRequestId(value: String) extends AnyVal

enum FriendRequestStatus {
  case Pending
  case Accepted
  case Rejected
}

object FriendRequestStatus {
  def fromWire(value: String): Option[FriendRequestStatus] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "pending"  => Some(FriendRequestStatus.Pending)
      case "accepted" => Some(FriendRequestStatus.Accepted)
      case "rejected" => Some(FriendRequestStatus.Rejected)
      case _          => None
    }

  def wireValue(value: FriendRequestStatus): String =
    value match {
      case FriendRequestStatus.Pending  => "pending"
      case FriendRequestStatus.Accepted => "accepted"
      case FriendRequestStatus.Rejected => "rejected"
    }
}

enum FriendRequestDecision {
  case Accepted
  case Rejected
}

object FriendRequestDecision {
  def fromWire(value: String): Option[FriendRequestDecision] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "accepted" => Some(FriendRequestDecision.Accepted)
      case "rejected" => Some(FriendRequestDecision.Rejected)
      case _          => None
    }

  def statusFor(value: FriendRequestDecision): FriendRequestStatus =
    value match {
      case FriendRequestDecision.Accepted => FriendRequestStatus.Accepted
      case FriendRequestDecision.Rejected => FriendRequestStatus.Rejected
    }

  def wireValue(value: FriendRequestDecision): String =
    value match {
      case FriendRequestDecision.Accepted => "accepted"
      case FriendRequestDecision.Rejected => "rejected"
    }
}

final case class FriendRequestRecord(
  id: FriendRequestId,
  sourceHandle: PlayerHandle,
  targetHandle: PlayerHandle,
  createdAt: EpochMillis,
  status: FriendRequestStatus,
  respondedAt: Option[EpochMillis]
)

object FriendRequestRecord {
  def pending(
    id: FriendRequestId,
    sourceHandle: PlayerHandle,
    targetHandle: PlayerHandle,
    createdAt: EpochMillis
  ): FriendRequestRecord =
    FriendRequestRecord(
      id = id,
      sourceHandle = sourceHandle,
      targetHandle = targetHandle,
      createdAt = createdAt,
      status = FriendRequestStatus.Pending,
      respondedAt = None
    )

  def respond(
    request: FriendRequestRecord,
    decision: FriendRequestDecision,
    respondedAt: EpochMillis
  ): FriendRequestRecord =
    request.copy(
      status = FriendRequestDecision.statusFor(decision),
      respondedAt = Some(respondedAt)
    )
}
