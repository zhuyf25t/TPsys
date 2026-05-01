package slaydemo.backend.social.database

import java.sql.{PreparedStatement, ResultSet, Types}
import java.util.UUID

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord, FriendRequestStatus}

final class PostgresFriendRequestRepository(settings: PostgresConnectionSettings) extends FriendRequestRepository {
  initialize()

  override def nextRequestId(): FriendRequestId =
    FriendRequestId(s"friend-${UUID.randomUUID().toString}")

  override def findById(id: FriendRequestId): Option[FriendRequestRecord] =
    queryOne(
      s"""SELECT $friendRequestColumns
         |FROM social_friend_requests
         |WHERE id = ?
         |LIMIT 1""".stripMargin,
      statement => statement.setString(1, id.value.trim)
    )

  override def findByHandles(source: PlayerHandle, target: PlayerHandle): Option[FriendRequestRecord] =
    queryOne(
      s"""SELECT $friendRequestColumns
         |FROM social_friend_requests
         |WHERE lower(source_handle) = lower(?) AND lower(target_handle) = lower(?)
         |LIMIT 1""".stripMargin,
      statement => {
        statement.setString(1, source.value.trim)
        statement.setString(2, target.value.trim)
      }
    )

  override def listByOwner(owner: PlayerHandle): Vector[FriendRequestRecord] =
    queryMany(
      s"""SELECT $friendRequestColumns
         |FROM social_friend_requests
         |WHERE lower(source_handle) = lower(?) OR lower(target_handle) = lower(?)
         |ORDER BY created_at DESC, id ASC""".stripMargin,
      statement => {
        statement.setString(1, owner.value.trim)
        statement.setString(2, owner.value.trim)
      }
    )

  override def createIfAbsent(record: FriendRequestRecord): FriendRequestStoreCreateResult = {
    val insertedRows = PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO social_friend_requests (id, source_handle, target_handle, created_at, status, responded_at)
          |VALUES (?, ?, ?, ?, ?, ?)
          |ON CONFLICT DO NOTHING""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    if insertedRows == 1 then FriendRequestStoreCreateResult.Created(record)
    else FriendRequestStoreCreateResult.AlreadyExists(findByHandles(record.sourceHandle, record.targetHandle).getOrElse(record))
  }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO social_friend_requests (id, source_handle, target_handle, created_at, status, responded_at)
          |VALUES (?, ?, ?, ?, ?, ?)
          |ON CONFLICT (id) DO UPDATE SET
          |  source_handle = EXCLUDED.source_handle,
          |  target_handle = EXCLUDED.target_handle,
          |  created_at = EXCLUDED.created_at,
          |  status = EXCLUDED.status,
          |  responded_at = EXCLUDED.responded_at""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  private def initialize(): Unit =
    PostgresSupport.withConnection(settings) { connection =>
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
        "CREATE UNIQUE INDEX IF NOT EXISTS social_friend_requests_source_target_unique_idx ON social_friend_requests (lower(source_handle), lower(target_handle))"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS social_friend_requests_owner_created_at_idx ON social_friend_requests (lower(source_handle), lower(target_handle), created_at DESC)"
      )(_.executeUpdate())
    }

  private val friendRequestColumns: String =
    "id, source_handle, target_handle, created_at, status, responded_at"

  private def queryOne(sql: String, bind: PreparedStatement => Unit): Option[FriendRequestRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then Some(readRecord(resultSet)) else None
        }
      }
    }

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Vector[FriendRequestRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[FriendRequestRecord]
          while resultSet.next() do records += readRecord(resultSet)
          records.result()
        }
      }
    }

  private def bindRecord(statement: PreparedStatement, record: FriendRequestRecord): Unit = {
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

  private def readRecord(resultSet: ResultSet): FriendRequestRecord = {
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
