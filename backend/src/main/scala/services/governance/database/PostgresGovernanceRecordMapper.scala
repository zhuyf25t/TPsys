package services.governance.database

import java.sql.{PreparedStatement, ResultSet, Types}

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

private[database] object PostgresGovernanceRecordMapper {
  def bindAdjustment(statement: PreparedStatement, record: ContributionAdjustmentRecord): Unit = {
    statement.setString(1, record.id.value)
    statement.setString(2, record.actorHandle.value)
    statement.setString(3, record.targetHandle.value)
    statement.setInt(4, record.delta.value)
    statement.setString(5, record.reason.value)
    statement.setLong(6, record.createdAt.value)
    statement.setString(7, record.sourceLabel.value)
    statement.setString(8, record.sourcePath.value)
  }

  def bindReviewNotification(statement: PreparedStatement, record: GovernanceReviewNotificationRecord): Unit = {
    statement.setString(1, record.id.value)
    statement.setString(2, record.actorHandle.value)
    statement.setString(3, GovernanceReviewKind.wireValue(record.kind))
    statement.setString(4, GovernanceReviewTargetType.wireValue(record.targetType))
    statement.setString(5, record.targetId.value)
    statement.setString(6, record.targetTitle.value)
    statement.setString(7, record.targetPath.value)
    statement.setString(8, record.body.value)
    statement.setLong(9, record.createdAt.value)
    statement.setString(10, record.mailId.value)
  }

  def bindOptionalStringPair(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) =>
        statement.setString(index, text)
        statement.setString(index + 1, text)
      case None =>
        statement.setNull(index, Types.VARCHAR)
        statement.setNull(index + 1, Types.VARCHAR)
    }

  def readAdjustments(resultSet: ResultSet): Vector[ContributionAdjustmentRecord] = {
    val records = Vector.newBuilder[ContributionAdjustmentRecord]
    while resultSet.next() do {
      records += ContributionAdjustmentRecord(
        id = ContributionAdjustmentId(resultSet.getString("id")),
        actorHandle = AdminHandle(resultSet.getString("actor_handle")),
        targetHandle = GovernanceTargetHandle(resultSet.getString("target_handle")),
        delta = ContributionDelta(resultSet.getInt("delta")),
        reason = GovernanceReason(resultSet.getString("reason")),
        createdAt = EpochMillis(resultSet.getLong("created_at")),
        sourceLabel = GovernanceSourceLabel(Option(resultSet.getString("source_label")).getOrElse("")),
        sourcePath = GovernanceSourcePath(Option(resultSet.getString("source_path")).getOrElse(""))
      )
    }
    records.result()
  }

  def readNotifications(resultSet: ResultSet): Vector[GovernanceReviewNotificationRecord] = {
    val records = Vector.newBuilder[GovernanceReviewNotificationRecord]
    while resultSet.next() do {
      records += GovernanceReviewNotificationRecord(
        id = GovernanceReviewNotificationId(resultSet.getString("id")),
        actorHandle = GovernanceActorHandle(resultSet.getString("actor_handle")),
        kind = readReviewKind(resultSet.getString("kind")),
        targetType = readReviewTargetType(resultSet.getString("target_type")),
        targetId = GovernanceReviewTargetId(resultSet.getString("target_id")),
        targetTitle = GovernanceReviewTargetTitle(resultSet.getString("target_title")),
        targetPath = GovernanceReviewTargetPath(resultSet.getString("target_path")),
        body = GovernanceReviewBody(resultSet.getString("body")),
        createdAt = EpochMillis(resultSet.getLong("created_at")),
        mailId = GovernanceMailSnapshotId(resultSet.getString("mail_id"))
      )
    }
    records.result()
  }

  private def readReviewKind(value: String): GovernanceReviewKind =
    GovernanceReviewKind.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid governance review kind in database: $rendered")
    }

  private def readReviewTargetType(value: String): GovernanceReviewTargetType =
    GovernanceReviewTargetType.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid governance review target type in database: $rendered")
    }
}
