package services.mail.database

import java.sql.Connection

import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[database] object PostgresMailSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
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
}
