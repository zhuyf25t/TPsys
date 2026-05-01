package slaydemo.backend.mail.objects

import java.util.Locale

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle

final case class MailId(value: String) extends AnyVal
final case class MailFriendRequestId(value: String) extends AnyVal
final case class GovernanceMailMetadata(
  actorHandle: String,
  targetPath: String,
  targetLabel: String
)
final case class FriendRequestMailMetadata(
  requestId: MailFriendRequestId,
  status: MailFriendRequestStatus,
  sourceHandle: PlayerHandle
)

enum MailKind {
  case System
  case Battle
  case Reward
  case Friend
  case Governance
}

object MailKind {
  def fromWire(value: String): Option[MailKind] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "system"     => Some(MailKind.System)
      case "battle"     => Some(MailKind.Battle)
      case "reward"     => Some(MailKind.Reward)
      case "friend"     => Some(MailKind.Friend)
      case "governance" => Some(MailKind.Governance)
      case _            => None
    }

  def wireValue(value: MailKind): String =
    value match {
      case MailKind.System     => "system"
      case MailKind.Battle     => "battle"
      case MailKind.Reward     => "reward"
      case MailKind.Friend     => "friend"
      case MailKind.Governance => "governance"
    }
}

enum MailFriendRequestStatus {
  case Pending
  case Accepted
  case Rejected
}

object MailFriendRequestStatus {
  def wireValue(value: MailFriendRequestStatus): String =
    value match {
      case MailFriendRequestStatus.Pending  => "pending"
      case MailFriendRequestStatus.Accepted => "accepted"
      case MailFriendRequestStatus.Rejected => "rejected"
    }

  def fromWire(value: String): Option[MailFriendRequestStatus] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "pending"  => Some(MailFriendRequestStatus.Pending)
      case "accepted" => Some(MailFriendRequestStatus.Accepted)
      case "rejected" => Some(MailFriendRequestStatus.Rejected)
      case _          => None
    }
}

final case class MailRecord(
  id: MailId,
  ownerHandle: PlayerHandle,
  kind: MailKind,
  subject: String,
  excerpt: String,
  senderLabel: String,
  unread: Boolean,
  important: Boolean,
  createdAt: EpochMillis,
  sourceBattleId: Option[String] = None,
  sourcePath: Option[String] = None,
  sourceLabel: Option[String] = None,
  governanceMetadata: Option[GovernanceMailMetadata] = None,
  friendRequestMetadata: Option[FriendRequestMailMetadata] = None
)
