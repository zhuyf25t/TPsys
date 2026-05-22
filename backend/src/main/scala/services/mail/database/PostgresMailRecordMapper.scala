package services.mail.database

import java.sql.{PreparedStatement, ResultSet, Types}

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle
import services.mail.objects.{
  FriendRequestMailMetadata,
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}

private[database] object PostgresMailRecordMapper {
  def bindRecord(statement: PreparedStatement, record: MailRecord): Unit = {
    statement.setString(1, record.id.value)
    statement.setString(2, record.ownerHandle.value)
    statement.setString(3, MailKind.wireValue(record.kind))
    statement.setString(4, record.subject)
    statement.setString(5, record.excerpt)
    statement.setString(6, record.senderLabel)
    statement.setBoolean(7, record.unread)
    statement.setBoolean(8, record.important)
    statement.setLong(9, record.createdAt.value)
    bindOptionalString(statement, 10, record.sourceBattleId)
    bindOptionalString(statement, 11, record.sourcePath)
    bindOptionalString(statement, 12, record.sourceLabel)
    bindOptionalString(statement, 13, record.governanceMetadata.map(_.actorHandle.value))
    bindOptionalString(statement, 14, record.governanceMetadata.map(_.targetPath.value))
    bindOptionalString(statement, 15, record.governanceMetadata.map(_.targetLabel.value))
    bindOptionalString(statement, 16, record.friendRequestMetadata.map(_.requestId.value))
    bindOptionalString(statement, 17, record.friendRequestMetadata.map(metadata => MailFriendRequestStatus.wireValue(metadata.status)))
    bindOptionalString(statement, 18, record.friendRequestMetadata.map(_.sourceHandle.value))
  }

  def readRecords(resultSet: ResultSet): Vector[MailRecord] = {
    val records = Vector.newBuilder[MailRecord]
    while (resultSet.next()) {
      records += readRecord(resultSet)
    }
    records.result()
  }

  def readRecord(resultSet: ResultSet): MailRecord =
    MailRecord(
      id = MailId(resultSet.getString("id")),
      ownerHandle = PlayerHandle(resultSet.getString("owner_handle")),
      kind = readKind(resultSet.getString("kind")),
      subject = resultSet.getString("subject"),
      excerpt = resultSet.getString("excerpt"),
      senderLabel = resultSet.getString("sender_label"),
      readState = MailReadState.fromUnreadFlag(resultSet.getBoolean("unread")),
      importance = MailImportance.fromImportantFlag(resultSet.getBoolean("important")),
      createdAt = EpochMillis(resultSet.getLong("created_at")),
      sourceBattleId = optionalColumn(resultSet, "source_battle_id"),
      sourcePath = optionalColumn(resultSet, "source_path"),
      sourceLabel = optionalColumn(resultSet, "source_label"),
      governanceMetadata = readGovernanceMetadata(resultSet),
      friendRequestMetadata = readFriendRequestMetadata(resultSet)
    )

  private def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) => statement.setString(index, text)
      case None       => statement.setNull(index, Types.VARCHAR)
    }

  private def readGovernanceMetadata(resultSet: ResultSet): Option[GovernanceMailMetadata] =
    for {
      actorHandle <- optionalColumn(resultSet, "governance_actor_handle")
      targetPath <- optionalColumn(resultSet, "governance_target_path")
      targetLabel <- optionalColumn(resultSet, "governance_target_label")
    } yield GovernanceMailMetadata(
      actorHandle = GovernanceMailActorHandle(actorHandle),
      targetPath = GovernanceMailTargetPath(targetPath),
      targetLabel = GovernanceMailTargetLabel(targetLabel)
    )

  private def readFriendRequestMetadata(resultSet: ResultSet): Option[FriendRequestMailMetadata] = {
    val requestId = optionalColumn(resultSet, "friend_request_id")
    val statusText = optionalColumn(resultSet, "friend_request_status")
    val sourceHandleText = optionalColumn(resultSet, "friend_request_source_handle")

    (requestId, statusText, sourceHandleText) match {
      case (None, None, None) =>
        None
      case (Some(id), Some(rawStatus), Some(rawSourceHandle)) =>
        val status = MailFriendRequestStatus.fromWire(rawStatus).getOrElse {
          throw new IllegalStateException(s"Invalid friend request mail status in database: $rawStatus")
        }
        val sourceHandle = PlayerHandle.forLookup(rawSourceHandle).getOrElse {
          throw new IllegalStateException(s"Invalid friend request mail source handle in database: $rawSourceHandle")
        }
        Some(
          FriendRequestMailMetadata(
            requestId = MailFriendRequestId(id),
            status = status,
            sourceHandle = sourceHandle
          )
        )
      case _ =>
        throw new IllegalStateException("Incomplete friend request mail metadata in database")
    }
  }

  private def readKind(value: String): MailKind =
    MailKind.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid mail kind in database: $rendered")
    }

  private def optionalColumn(resultSet: ResultSet, columnName: String): Option[String] =
    Option(resultSet.getString(columnName)).map(_.trim).filter(_.nonEmpty)
}
