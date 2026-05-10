package slaydemo.backend.social.database

import java.sql.{PreparedStatement, ResultSet, Types}

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord, FriendRequestStatus}

private[database] object PostgresFriendRequestRecordMapper {
  def bindRecord(statement: PreparedStatement, record: FriendRequestRecord): Unit = {
    statement.setString(1, record.id.value)
    statement.setString(2, record.sourceHandle.value)
    statement.setString(3, record.targetHandle.value)
    statement.setLong(4, record.createdAt.value)
    statement.setString(5, FriendRequestStatus.wireValue(record.status))
    record.respondedAt match {
      case Some(value) => statement.setLong(6, value.value)
      case None        => statement.setNull(6, Types.BIGINT)
    }
  }

  def readRecord(resultSet: ResultSet): FriendRequestRecord = {
    val respondedAt = resultSet.getLong("responded_at")
    FriendRequestRecord(
      id = FriendRequestId(resultSet.getString("id")),
      sourceHandle = PlayerHandle(resultSet.getString("source_handle")),
      targetHandle = PlayerHandle(resultSet.getString("target_handle")),
      createdAt = EpochMillis(resultSet.getLong("created_at")),
      status = readStatus(resultSet.getString("status")),
      respondedAt = if resultSet.wasNull() then None else Some(EpochMillis(respondedAt))
    )
  }

  private def readStatus(value: String): FriendRequestStatus =
    FriendRequestStatus.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid friend request status in database: $rendered")
    }
}
