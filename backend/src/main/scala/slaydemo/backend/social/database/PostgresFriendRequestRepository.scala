package slaydemo.backend.social.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.social.objects.FriendRequestRecord

final class PostgresFriendRequestRepository(config: PostgresConfig) extends FriendRequestRepository {
  initialize()

  override def findByHandles(sourceHandle: String, targetHandle: String): Option[FriendRequestRecord] = {
    queryOne(
      """SELECT id, source_handle, target_handle, created_at
        |FROM social_friend_requests
        |WHERE lower(source_handle) = lower(?) AND lower(target_handle) = lower(?)
        |LIMIT 1""".stripMargin,
      statement => {
        statement.setString(1, sourceHandle.trim)
        statement.setString(2, targetHandle.trim)
      }
    )
  }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO social_friend_requests (id, source_handle, target_handle, created_at)
          |VALUES (?, ?, ?, ?)""".stripMargin
      ) { statement =>
        statement.setString(1, record.id)
        statement.setString(2, record.sourceHandle)
        statement.setString(3, record.targetHandle)
        statement.setLong(4, record.createdAt)
        statement.executeUpdate()
      }
    }

    record
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS social_friend_requests (
          |  id TEXT PRIMARY KEY,
          |  source_handle TEXT NOT NULL,
          |  target_handle TEXT NOT NULL,
          |  created_at BIGINT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS social_friend_requests_source_target_idx ON social_friend_requests (lower(source_handle), lower(target_handle))"
      )(_.executeUpdate())
    }
  }

  private def queryOne(sql: String, bind: PreparedStatement => Unit): Option[FriendRequestRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readRecord(resultSet)) else None
        }
      }
    }
  }

  private def readRecord(resultSet: ResultSet): FriendRequestRecord = {
    FriendRequestRecord(
      id = resultSet.getString("id"),
      sourceHandle = resultSet.getString("source_handle"),
      targetHandle = resultSet.getString("target_handle"),
      createdAt = resultSet.getLong("created_at")
    )
  }
}
