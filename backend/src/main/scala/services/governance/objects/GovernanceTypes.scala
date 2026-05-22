package services.governance.objects

import java.util.Locale

import services.battle.objects.EpochMillis
import services.mail.objects.{GovernanceMailMetadata, MailImportance, MailKind, MailReadState}

final case class AdminHandle(value: String) extends AnyVal {
  def key: String = value.toLowerCase(Locale.ROOT)
}

object AdminHandle {
  def fromString(value: String): Option[AdminHandle] = {
    val trimmed = Option(value).getOrElse("").trim
    Option.when(trimmed.equalsIgnoreCase("admin"))(AdminHandle(trimmed))
  }
}

final case class GovernanceActorHandle(value: String) extends AnyVal
final case class GovernanceTargetHandle(value: String) extends AnyVal {
  def key: String = value.toLowerCase(Locale.ROOT)
}

final case class ContributionAdjustmentId(value: String) extends AnyVal
final case class ContributionDelta(value: Int) extends AnyVal
final case class GovernanceReason(value: String) extends AnyVal
final case class GovernanceSourceLabel(value: String) extends AnyVal
final case class GovernanceSourcePath(value: String) extends AnyVal

final case class GovernanceReviewNotificationId(value: String) extends AnyVal
final case class GovernanceReviewTargetId(value: String) extends AnyVal
final case class GovernanceReviewTargetTitle(value: String) extends AnyVal
final case class GovernanceReviewTargetPath(value: String) extends AnyVal
final case class GovernanceReviewBody(value: String) extends AnyVal

final case class GovernanceMailSnapshotId(value: String) extends AnyVal

enum GovernanceReviewKind {
  case ReplayProposal
  case ReplayReport
  case DiscussionReport
  case BotSuggestion
}

object GovernanceReviewKind {
  def fromWire(value: String): Option[GovernanceReviewKind] =
    Option(value).map(_.trim) match {
      case Some("replay_proposal")   => Some(GovernanceReviewKind.ReplayProposal)
      case Some("replay_report")     => Some(GovernanceReviewKind.ReplayReport)
      case Some("discussion_report") => Some(GovernanceReviewKind.DiscussionReport)
      case Some("bot_suggestion")    => Some(GovernanceReviewKind.BotSuggestion)
      case _                         => None
    }

  def wireValue(value: GovernanceReviewKind): String =
    value match {
      case GovernanceReviewKind.ReplayProposal  => "replay_proposal"
      case GovernanceReviewKind.ReplayReport    => "replay_report"
      case GovernanceReviewKind.DiscussionReport => "discussion_report"
      case GovernanceReviewKind.BotSuggestion   => "bot_suggestion"
    }

  def displayLabel(value: GovernanceReviewKind): String =
    value match {
      case GovernanceReviewKind.ReplayProposal  => "Replay proposal"
      case GovernanceReviewKind.ReplayReport    => "Replay report"
      case GovernanceReviewKind.DiscussionReport => "Discussion report"
      case GovernanceReviewKind.BotSuggestion   => "Bot suggestion"
    }
}

enum GovernanceReviewTargetType {
  case Replay
  case Discussion
  case Bot
}

object GovernanceReviewTargetType {
  def fromWire(value: String): Option[GovernanceReviewTargetType] =
    Option(value).map(_.trim) match {
      case Some("replay")     => Some(GovernanceReviewTargetType.Replay)
      case Some("discussion") => Some(GovernanceReviewTargetType.Discussion)
      case Some("bot")        => Some(GovernanceReviewTargetType.Bot)
      case _                  => None
    }

  def wireValue(value: GovernanceReviewTargetType): String =
    value match {
      case GovernanceReviewTargetType.Replay     => "replay"
      case GovernanceReviewTargetType.Discussion => "discussion"
      case GovernanceReviewTargetType.Bot        => "bot"
    }
}

final case class ContributionAdjustmentRecord(
  id: ContributionAdjustmentId,
  actorHandle: AdminHandle,
  targetHandle: GovernanceTargetHandle,
  delta: ContributionDelta,
  reason: GovernanceReason,
  createdAt: EpochMillis,
  sourceLabel: GovernanceSourceLabel,
  sourcePath: GovernanceSourcePath
)

final case class GovernanceReviewNotificationRecord(
  id: GovernanceReviewNotificationId,
  actorHandle: GovernanceActorHandle,
  kind: GovernanceReviewKind,
  targetType: GovernanceReviewTargetType,
  targetId: GovernanceReviewTargetId,
  targetTitle: GovernanceReviewTargetTitle,
  targetPath: GovernanceReviewTargetPath,
  body: GovernanceReviewBody,
  createdAt: EpochMillis,
  mailId: GovernanceMailSnapshotId
)

final case class GovernanceMailSnapshot(
  id: GovernanceMailSnapshotId,
  ownerHandle: GovernanceTargetHandle,
  kind: MailKind,
  subject: String,
  excerpt: String,
  senderLabel: String,
  readState: MailReadState,
  importance: MailImportance,
  createdAt: EpochMillis,
  governanceMetadata: Option[GovernanceMailMetadata]
) {
  def unread: Boolean =
    MailReadState.unreadFlag(readState)

  def important: Boolean =
    MailImportance.importantFlag(importance)
}
