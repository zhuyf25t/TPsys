package services.governance.database

import services.battle.objects.EpochMillis
import services.governance.objects.{
  AdminHandle,
  ContributionAdjustmentId,
  ContributionAdjustmentRecord,
  ContributionDelta,
  GovernanceActorHandle,
  GovernanceMailSnapshotId,
  GovernanceReason,
  GovernanceReviewBody,
  GovernanceReviewKind,
  GovernanceReviewNotificationId,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetId,
  GovernanceReviewTargetPath,
  GovernanceReviewTargetTitle,
  GovernanceReviewTargetType,
  GovernanceSourceLabel,
  GovernanceSourcePath,
  GovernanceTargetHandle
}

private[database] object GovernanceFileJsonParser {
  def parseAdjustments(raw: String): Vector[ContributionAdjustmentRecord] =
    GovernanceFileJsonObjectScanner.extractArrayObjects(raw, "adjustments").flatMap(parseAdjustment)

  def parseReviewNotifications(raw: String): Vector[GovernanceReviewNotificationRecord] =
    GovernanceFileJsonObjectScanner.extractArrayObjects(raw, "notifications").flatMap(parseReviewNotification)

  private def parseAdjustment(chunk: String): Option[ContributionAdjustmentRecord] =
    for {
      id <- extractString(chunk, "id")
      actorHandle <- extractString(chunk, "actorHandle")
      targetHandle <- extractString(chunk, "targetHandle")
      delta <- extractInt(chunk, "delta")
      reason <- extractString(chunk, "reason")
      createdAt <- extractLong(chunk, "createdAt")
    } yield ContributionAdjustmentRecord(
      id = ContributionAdjustmentId(id),
      actorHandle = AdminHandle(actorHandle),
      targetHandle = GovernanceTargetHandle(targetHandle),
      delta = ContributionDelta(delta),
      reason = GovernanceReason(reason),
      createdAt = EpochMillis(createdAt),
      sourceLabel = GovernanceSourceLabel(extractString(chunk, "sourceLabel").getOrElse("")),
      sourcePath = GovernanceSourcePath(extractString(chunk, "sourcePath").getOrElse(""))
    )

  private def parseReviewNotification(chunk: String): Option[GovernanceReviewNotificationRecord] =
    for {
      id <- extractString(chunk, "id")
      actorHandle <- extractString(chunk, "actorHandle")
      kindText <- extractString(chunk, "kind")
      kind <- GovernanceReviewKind.fromWire(kindText)
      targetTypeText <- extractString(chunk, "targetType")
      targetType <- GovernanceReviewTargetType.fromWire(targetTypeText)
      targetId <- extractString(chunk, "targetId")
      targetTitle <- extractString(chunk, "targetTitle")
      targetPath <- extractString(chunk, "targetPath")
      body <- extractString(chunk, "body")
      createdAt <- extractLong(chunk, "createdAt")
      mailId <- extractString(chunk, "mailId")
    } yield GovernanceReviewNotificationRecord(
      id = GovernanceReviewNotificationId(id),
      actorHandle = GovernanceActorHandle(actorHandle),
      kind = kind,
      targetType = targetType,
      targetId = GovernanceReviewTargetId(targetId),
      targetTitle = GovernanceReviewTargetTitle(targetTitle),
      targetPath = GovernanceReviewTargetPath(targetPath),
      body = GovernanceReviewBody(body),
      createdAt = EpochMillis(createdAt),
      mailId = GovernanceMailSnapshotId(mailId)
    )

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).flatMap(_.group(1).toIntOption)
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).flatMap(_.group(1).toLongOption)
  }

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")
}
