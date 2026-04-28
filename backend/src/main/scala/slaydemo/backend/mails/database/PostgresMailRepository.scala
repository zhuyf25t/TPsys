package slaydemo.backend.mails.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}

final class PostgresMailRepository(config: PostgresConfig) extends MailRepository {
  initialize()

  override def listByOwner(ownerHandle: String): Seq[MailRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, owner_handle, kind, subject, excerpt, sender_label, unread, important, created_at
          |FROM mails
          |WHERE lower(owner_handle) = lower(?)
          |ORDER BY created_at DESC, id DESC""".stripMargin
      ) { statement =>
        statement.setString(1, ownerHandle.trim)
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }
  }

  override def save(record: MailRecord): MailRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO mails (
          |  id, owner_handle, kind, subject, excerpt, sender_label, unread, important, created_at
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (owner_handle, id) DO NOTHING""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  override def markRead(ownerHandle: String, mailId: String): Boolean = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE mails
          |SET unread = FALSE
          |WHERE id = ? AND lower(owner_handle) = lower(?)
          |RETURNING id""".stripMargin
      ) { statement =>
        statement.setString(1, mailId)
        statement.setString(2, ownerHandle.trim)
        PostgresSupport.withResultSet(statement)(_.next())
      }
    }
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
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
          |  PRIMARY KEY (owner_handle, id)
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """DO $$
          |DECLARE
          |  primary_key_name text;
          |  primary_key_columns text[];
          |BEGIN
          |  SELECT constraint_info.conname, constraint_info.columns
          |  INTO primary_key_name, primary_key_columns
          |  FROM (
          |    SELECT constraint_record.conname, array_agg(attribute.attname::text ORDER BY key_column.ordinality) AS columns
          |    FROM pg_constraint constraint_record
          |    JOIN unnest(constraint_record.conkey) WITH ORDINALITY AS key_column(attnum, ordinality) ON true
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
        "CREATE INDEX IF NOT EXISTS mails_owner_handle_created_at_idx ON mails (lower(owner_handle), created_at DESC)"
      )(_.executeUpdate())
    }
  }

  private def bindRecord(statement: PreparedStatement, record: MailRecord): Unit = {
    statement.setString(1, record.id)
    statement.setString(2, record.ownerHandle)
    statement.setString(3, record.kind)
    statement.setString(4, record.subject)
    statement.setString(5, record.excerpt)
    statement.setString(6, record.senderLabel)
    statement.setBoolean(7, record.unread)
    statement.setBoolean(8, record.important)
    statement.setLong(9, record.createdAt)
  }

  private def readRecords(resultSet: ResultSet): Seq[MailRecord] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[MailRecord]
    while (resultSet.next()) {
      buffer += readRecord(resultSet)
    }
    buffer.toSeq
  }

  private def readRecord(resultSet: ResultSet): MailRecord = {
    MailRecord(
      id = resultSet.getString("id"),
      ownerHandle = resultSet.getString("owner_handle"),
      kind = resultSet.getString("kind"),
      subject = resultSet.getString("subject"),
      excerpt = resultSet.getString("excerpt"),
      senderLabel = resultSet.getString("sender_label"),
      unread = resultSet.getBoolean("unread"),
      important = resultSet.getBoolean("important"),
      createdAt = resultSet.getLong("created_at")
    )
  }
}
