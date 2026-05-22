package services.mail.objects

import java.util.Locale

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle

final case class MailId(value: String) extends AnyVal
final case class MailFriendRequestId(value: String) extends AnyVal
final case class GovernanceMailActorHandle(value: String) extends AnyVal
final case class GovernanceMailTargetPath(value: String) extends AnyVal
final case class GovernanceMailTargetLabel(value: String) extends AnyVal

final case class GovernanceMailMetadata(
  actorHandle: GovernanceMailActorHandle,
  targetPath: GovernanceMailTargetPath,
  targetLabel: GovernanceMailTargetLabel
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

enum MailReadState {
  case Unread
  case Read
}

object MailReadState {
  def fromUnreadFlag(unread: Boolean): MailReadState =
    if unread then MailReadState.Unread else MailReadState.Read

  def unreadFlag(value: MailReadState): Boolean =
    value == MailReadState.Unread
}

enum MailImportance {
  case Normal
  case Important
}

object MailImportance {
  def fromImportantFlag(important: Boolean): MailImportance =
    if important then MailImportance.Important else MailImportance.Normal

  def importantFlag(value: MailImportance): Boolean =
    value == MailImportance.Important
}

final case class MailRecord(
  id: MailId,
  ownerHandle: PlayerHandle,
  kind: MailKind,
  subject: String,
  excerpt: String,
  senderLabel: String,
  readState: MailReadState,
  importance: MailImportance,
  createdAt: EpochMillis,
  sourceBattleId: Option[String],
  sourcePath: Option[String],
  sourceLabel: Option[String],
  governanceMetadata: Option[GovernanceMailMetadata],
  friendRequestMetadata: Option[FriendRequestMailMetadata]
) {
  def unread: Boolean =
    MailReadState.unreadFlag(readState)

  def important: Boolean =
    MailImportance.importantFlag(importance)

  def markRead: MailRecord =
    copy(readState = MailReadState.Read)
}
