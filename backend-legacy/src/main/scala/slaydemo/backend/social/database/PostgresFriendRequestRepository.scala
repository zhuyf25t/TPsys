package slaydemo.backend.social.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.social.objects.FriendRequestRecord

final class PostgresFriendRequestRepository(config: PostgresConfig) extends FriendRequestRepository {
  initialize()

  override def findById(id: String): Option[FriendRequestRecord] = {
    queryOne(
      """SELECT id, source_handle, target_handle, created_at, status, responded_at
        |FROM social_friend_requests
        |WHERE id = ?
        |LIMIT 1""".stripMargin,
      statement => statement.setString(1, id.trim)
    )
  }

  override def findByHandles(sourceHandle: String, targetHandle: String): Option[FriendRequestRecord] = {
    queryOne(
      """SELECT id, source_handle, target_handle, created_at, status, responded_at
        |FROM social_friend_requests
        |WHERE lower(source_handle) = lower(?) AND lower(target_handle) = lower(?)
        |LIMIT 1""".stripMargin,
      statement => {
        statement.setString(1, sourceHandle.trim)
        statement.setString(2, targetHandle.trim)
      }
    )
  }

  override def listByOwner(ownerHandle: String): Seq[FriendRequestRecord] = {
    queryMany(
      """SELECT id, source_handle, target_handle, created_at, status, responded_at
        |FROM social_friend_requests
        |WHERE lower(source_handle) = lower(?) OR lower(target_handle) = lower(?)
        |ORDER BY created_at DESC, id DESC""".stripMargin,
      statement => {
        statement.setString(1, ownerHandle.trim)
        statement.setString(2, ownerHandle.trim)
      }
    )
  }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO social_friend_requests (id, source_handle, target_handle, created_at, status, responded_at)
          |VALUES (?, ?, ?, ?, ?, ?)""".stripMargin
      ) { statement =>
        statement.setString(1, record.id)
        statement.setString(2, record.sourceHandle)
        statement.setString(3, record.targetHandle)
        statement.setLong(4, record.createdAt)
        statement.setString(5, record.status)
        record.respondedAt match {
          case Some(value) => statement.setLong(6, value)
          case None        => statement.setNull(6, java.sql.Types.BIGINT)
        }
        statement.executeUpdate()
      }
    }

    record
  }

  override def updateStatus(id: String, status: String, respondedAt: Long): Option[FriendRequestRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE social_friend_requests
          |SET status = ?, responded_at = ?
          |WHERE id = ? AND status = 'pending'
          |RETURNING id, source_handle, target_handle, created_at, status, responded_at""".stripMargin
      ) { statement =>
        statement.setString(1, status)
        statement.setLong(2, respondedAt)
        statement.setString(3, id.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readRecord(resultSet)) else None
        }
      }
    }
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS social_friend_requests (
          |  id TEXT PRIMARY KEY,
          |  source_handle TEXT NOT NULL,
          |  target_handle TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  status TEXT NOT NULL DEFAULT 'pending',
          |  responded_at BIGINT
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE social_friend_requests ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'pending'"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE social_friend_requests ADD COLUMN IF NOT EXISTS responded_at BIGINT"
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

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Seq[FriendRequestRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val buffer = scala.collection.mutable.ArrayBuffer.empty[FriendRequestRecord]
          while (resultSet.next()) {
            buffer += readRecord(resultSet)
          }
          buffer.toSeq
        }
      }
    }
  }

  private def readRecord(resultSet: ResultSet): FriendRequestRecord = {
    FriendRequestRecord(
      id = resultSet.getString("id"),
      sourceHandle = resultSet.getString("source_handle"),
      targetHandle = resultSet.getString("target_handle"),
      createdAt = resultSet.getLong("created_at"),
      status = Option(resultSet.getString("status")).getOrElse("pending"),
      respondedAt = {
        val value = resultSet.getLong("responded_at")
        if (resultSet.wasNull()) None else Some(value)
      }
    )
  }
}
