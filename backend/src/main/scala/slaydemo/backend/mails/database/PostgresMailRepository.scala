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
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
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
          |WHERE id = ? AND lower(owner_handle) = lower(?) AND unread = TRUE
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
          |  id TEXT PRIMARY KEY,
          |  owner_handle TEXT NOT NULL,
          |  kind TEXT NOT NULL,
          |  subject TEXT NOT NULL,
          |  excerpt TEXT NOT NULL,
          |  sender_label TEXT NOT NULL,
          |  unread BOOLEAN NOT NULL,
          |  important BOOLEAN NOT NULL,
          |  created_at BIGINT NOT NULL
          |)""".stripMargin
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
