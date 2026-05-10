package slaydemo.backend.mail.database

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailId, MailRecord}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresMailRepository(settings: PostgresConnectionSettings) extends MailRepository {
  PostgresMailSchema.initialize(settings)

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
        PostgresSupport.withResultSet(statement)(PostgresMailRecordMapper.readRecords)
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
        PostgresMailRecordMapper.bindRecord(statement, record)
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
          if (resultSet.next()) Some(PostgresMailRecordMapper.readRecord(resultSet)) else None
        }
      }
    }
}
