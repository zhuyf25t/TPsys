package slaydemo.backend.social.database

import java.sql.PreparedStatement

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings
import slaydemo.backend.social.objects.{FriendRequestId, FriendRequestRecord}

final class PostgresFriendRequestRepository(
  settings: PostgresConnectionSettings,
  idGenerator: FriendRequestIdGenerator = RandomFriendRequestIdGenerator
) extends FriendRequestRepository {
  PostgresFriendRequestSchema.initialize(settings)

  override def nextRequestId(): FriendRequestId =
    idGenerator.nextId()

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
         |ORDER BY
         |  CASE status
         |    WHEN 'pending' THEN 0
         |    WHEN 'accepted' THEN 1
         |    WHEN 'rejected' THEN 2
         |    ELSE 3
         |  END ASC,
         |  COALESCE(responded_at, created_at) DESC,
         |  created_at DESC,
         |  id ASC
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
    findByHandles(record.sourceHandle, record.targetHandle) match {
      case Some(existing) =>
        FriendRequestStoreCreateResult.AlreadyExists(existing)
      case None =>
        val insertedRows = PostgresSupport.withTransactionConnection(settings) { connection =>
          PostgresSupport.withStatement(
            connection,
            """INSERT INTO social_friend_requests (id, source_handle, target_handle, created_at, status, responded_at)
              |VALUES (?, ?, ?, ?, ?, ?)
              |ON CONFLICT DO NOTHING""".stripMargin
          ) { statement =>
            PostgresFriendRequestRecordMapper.bindRecord(statement, record)
            statement.executeUpdate()
          }
        }

        if insertedRows == 1 then FriendRequestStoreCreateResult.Created(record)
        else FriendRequestStoreCreateResult.AlreadyExists(findByHandles(record.sourceHandle, record.targetHandle).getOrElse(record))
    }
  }

  override def save(record: FriendRequestRecord): FriendRequestRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
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
        PostgresFriendRequestRecordMapper.bindRecord(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  private val friendRequestColumns: String =
    "id, source_handle, target_handle, created_at, status, responded_at"

  private def queryOne(sql: String, bind: PreparedStatement => Unit): Option[FriendRequestRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then Some(PostgresFriendRequestRecordMapper.readRecord(resultSet)) else None
        }
      }
    }

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Vector[FriendRequestRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[FriendRequestRecord]
          while resultSet.next() do records += PostgresFriendRequestRecordMapper.readRecord(resultSet)
          records.result()
        }
      }
    }
}
