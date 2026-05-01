package slaydemo.backend.mail.database

import java.sql.{Connection, PreparedStatement, ResultSet, Types}

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{
  FriendRequestMailMetadata,
  GovernanceMailMetadata,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailKind,
  MailRecord
}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresMailRepository(settings: PostgresConnectionSettings) extends MailRepository {
  initialize()

  override def listByOwner(owner: PlayerHandle): Vector[MailRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, owner_handle, kind, subject, excerpt, sender_label, unread, important, created_at,
          |  source_battle_id, source_path, source_label,
          |  governance_actor_handle, governance_target_path, governance_target_label,
          |  friend_request_id, friend_request_status, friend_request_source_handle
          |FROM mails
          |WHERE lower(owner_handle) = lower(?)
          |ORDER BY created_at DESC, id DESC""".stripMargin
      ) { statement =>
        statement.setString(1, owner.value.trim)
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }

  override def save(record: MailRecord): MailRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO mails (
          |  id, owner_handle, kind, subject, excerpt, sender_label, unread, important, created_at,
          |  source_battle_id, source_path, source_label,
          |  governance_actor_handle, governance_target_path, governance_target_label,
          |  friend_request_id, friend_request_status, friend_request_source_handle
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (owner_handle, id) DO UPDATE SET
          |  kind = EXCLUDED.kind,
          |  subject = EXCLUDED.subject,
          |  excerpt = EXCLUDED.excerpt,
          |  sender_label = EXCLUDED.sender_label,
          |  unread = EXCLUDED.unread,
          |  important = EXCLUDED.important,
          |  created_at = EXCLUDED.created_at,
          |  source_battle_id = EXCLUDED.source_battle_id,
          |  source_path = EXCLUDED.source_path,
          |  source_label = EXCLUDED.source_label,
          |  governance_actor_handle = EXCLUDED.governance_actor_handle,
          |  governance_target_path = EXCLUDED.governance_target_path,
          |  governance_target_label = EXCLUDED.governance_target_label,
          |  friend_request_id = EXCLUDED.friend_request_id,
          |  friend_request_status = EXCLUDED.friend_request_status,
          |  friend_request_source_handle = EXCLUDED.friend_request_source_handle""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  override def markRead(owner: PlayerHandle, mailId: MailId): Option[MailRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE mails
          |SET unread = FALSE
          |WHERE id = ? AND lower(owner_handle) = lower(?)
          |RETURNING id, owner_handle, kind, subject, excerpt, sender_label, unread, important, created_at,
          |  source_battle_id, source_path, source_label,
          |  governance_actor_handle, governance_target_path, governance_target_label,
          |  friend_request_id, friend_request_status, friend_request_source_handle""".stripMargin
      ) { statement =>
        statement.setString(1, mailId.value)
        statement.setString(2, owner.value.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readRecord(resultSet)) else None
        }
      }
    }

  private def initialize(): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS mails (
          |  id TEXT NOT NULL,
          |  owner_handle TEXT NOT NULL,
          |  kind TEXT NOT NULL,
          |  subject TEXT NOT NULL,
          |  excerpt TEXT NOT NULL,
          |  sender_label TEXT NOT NULL,
          |  unread BOOLEAN NOT NULL,
          |  important BOOLEAN NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  source_battle_id TEXT NULL,
          |  source_path TEXT NULL,
          |  source_label TEXT NULL,
          |  governance_actor_handle TEXT NULL,
          |  governance_target_path TEXT NULL,
          |  governance_target_label TEXT NULL,
          |  friend_request_id TEXT NULL,
          |  friend_request_status TEXT NULL,
          |  friend_request_source_handle TEXT NULL,
          |  PRIMARY KEY (owner_handle, id)
          |)""".stripMargin
      )(_.executeUpdate())

      requiredColumns.foreach(addColumnIfMissing(connection, _))

      PostgresSupport.withStatement(
        connection,
        """DO $$
          |DECLARE
          |  primary_key_name TEXT;
          |  primary_key_columns TEXT[];
          |BEGIN
          |  SELECT constraint_info.conname, constraint_info.columns
          |  INTO primary_key_name, primary_key_columns
          |  FROM (
          |    SELECT constraint_record.conname, array_agg(attribute.attname::TEXT ORDER BY key_column.ordinality) AS columns
          |    FROM pg_constraint constraint_record
          |    JOIN unnest(constraint_record.conkey) WITH ORDINALITY AS key_column(attnum, ordinality) ON TRUE
          |    JOIN pg_attribute attribute
          |      ON attribute.attrelid = constraint_record.conrelid
          |      AND attribute.attnum = key_column.attnum
          |    WHERE constraint_record.conrelid = 'mails'::regclass
          |      AND constraint_record.contype = 'p'
          |    GROUP BY constraint_record.conname
          |  ) constraint_info;
          |
          |  IF primary_key_name IS NOT NULL AND primary_key_columns IS DISTINCT FROM ARRAY['owner_handle', 'id'] THEN
          |    EXECUTE format('ALTER TABLE mails DROP CONSTRAINT %I', primary_key_name);
          |    primary_key_name := NULL;
          |  END IF;
          |
          |  IF primary_key_name IS NULL THEN
          |    ALTER TABLE mails ADD CONSTRAINT mails_owner_id_pkey PRIMARY KEY (owner_handle, id);
          |  END IF;
          |END $$""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE UNIQUE INDEX IF NOT EXISTS mails_owner_id_unique_idx ON mails (owner_handle, id)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS mails_owner_handle_created_at_idx ON mails (lower(owner_handle), created_at DESC)"
      )(_.executeUpdate())
    }

  private def requiredColumns: Vector[String] =
    Vector(
      "id TEXT NOT NULL DEFAULT ''",
      "owner_handle TEXT NOT NULL DEFAULT ''",
      "kind TEXT NOT NULL DEFAULT 'system'",
      "subject TEXT NOT NULL DEFAULT ''",
      "excerpt TEXT NOT NULL DEFAULT ''",
      "sender_label TEXT NOT NULL DEFAULT ''",
      "unread BOOLEAN NOT NULL DEFAULT TRUE",
      "important BOOLEAN NOT NULL DEFAULT FALSE",
      "created_at BIGINT NOT NULL DEFAULT 0",
      "source_battle_id TEXT",
      "source_path TEXT",
      "source_label TEXT",
      "governance_actor_handle TEXT",
      "governance_target_path TEXT",
      "governance_target_label TEXT",
      "friend_request_id TEXT",
      "friend_request_status TEXT",
      "friend_request_source_handle TEXT"
    )

  private def addColumnIfMissing(connection: Connection, columnDefinition: String): Unit =
    PostgresSupport.withStatement(
      connection,
      s"ALTER TABLE mails ADD COLUMN IF NOT EXISTS $columnDefinition"
    )(_.executeUpdate())

  private def bindRecord(statement: PreparedStatement, record: MailRecord): Unit = {
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
    bindOptionalString(statement, 13, record.governanceMetadata.map(_.actorHandle))
    bindOptionalString(statement, 14, record.governanceMetadata.map(_.targetPath))
    bindOptionalString(statement, 15, record.governanceMetadata.map(_.targetLabel))
    bindOptionalString(statement, 16, record.friendRequestMetadata.map(_.requestId.value))
    bindOptionalString(statement, 17, record.friendRequestMetadata.map(metadata => MailFriendRequestStatus.wireValue(metadata.status)))
    bindOptionalString(statement, 18, record.friendRequestMetadata.map(_.sourceHandle.value))
  }

  private def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) => statement.setString(index, text)
      case None       => statement.setNull(index, Types.VARCHAR)
    }

  private def readRecords(resultSet: ResultSet): Vector[MailRecord] = {
    val records = Vector.newBuilder[MailRecord]
    while (resultSet.next()) {
      records += readRecord(resultSet)
    }
    records.result()
  }

  private def readRecord(resultSet: ResultSet): MailRecord =
    MailRecord(
      id = MailId(resultSet.getString("id")),
      ownerHandle = PlayerHandle(resultSet.getString("owner_handle")),
      kind = readKind(resultSet.getString("kind")),
      subject = resultSet.getString("subject"),
      excerpt = resultSet.getString("excerpt"),
      senderLabel = resultSet.getString("sender_label"),
      unread = resultSet.getBoolean("unread"),
      important = resultSet.getBoolean("important"),
      createdAt = EpochMillis(resultSet.getLong("created_at")),
      sourceBattleId = optionalColumn(resultSet, "source_battle_id"),
      sourcePath = optionalColumn(resultSet, "source_path"),
      sourceLabel = optionalColumn(resultSet, "source_label"),
      governanceMetadata = readGovernanceMetadata(resultSet),
      friendRequestMetadata = readFriendRequestMetadata(resultSet)
    )

  private def readGovernanceMetadata(resultSet: ResultSet): Option[GovernanceMailMetadata] =
    for {
      actorHandle <- optionalColumn(resultSet, "governance_actor_handle")
      targetPath <- optionalColumn(resultSet, "governance_target_path")
      targetLabel <- optionalColumn(resultSet, "governance_target_label")
    } yield GovernanceMailMetadata(
      actorHandle = actorHandle,
      targetPath = targetPath,
      targetLabel = targetLabel
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
