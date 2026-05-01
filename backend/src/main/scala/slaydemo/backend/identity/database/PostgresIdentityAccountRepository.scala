package slaydemo.backend.identity.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.identity.objects.{
  AccountStatus,
  DisplayName,
  IdentityAccount,
  PasswordHash,
  PlainTextPassword,
  PlayerHandle,
  SessionToken,
  SkinId
}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.objects.UserId
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresIdentityAccountRepository(settings: PostgresConnectionSettings) extends IdentityAccountRepository {
  initialize()

  override def findByHandle(handle: PlayerHandle): Option[IdentityAccount] =
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token
        |FROM identity_accounts
        |WHERE lower(handle) = lower(?) AND active = TRUE""".stripMargin,
      statement => statement.setString(1, handle.value.trim)
    )

  override def findBySessionToken(sessionToken: SessionToken): Option[IdentityAccount] =
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token
        |FROM identity_accounts
        |WHERE session_token = ? AND session_token <> '' AND active = TRUE""".stripMargin,
      statement => statement.setString(1, sessionToken.value)
    )

  override def authenticate(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token
        |FROM identity_accounts
        |WHERE lower(handle) = lower(?) AND password = ? AND active = TRUE""".stripMargin,
      statement => {
        statement.setString(1, handle.value.trim)
        statement.setString(2, passwordHash.value)
      }
    )

  override def authenticateLegacyPlaintext(handle: PlayerHandle, password: PlainTextPassword): Option[IdentityAccount] =
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token
        |FROM identity_accounts
        |WHERE lower(handle) = lower(?)
        |  AND password = ?
        |  AND password !~ '^[0-9a-f]{64}$'
        |  AND active = TRUE""".stripMargin,
      statement => {
        statement.setString(1, handle.value.trim)
        statement.setString(2, password.value)
      }
    )

  override def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult = {
    val insertedRows = PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO identity_accounts (
          |  user_id, handle, display_name, skin_id, session_token, active, password
          |) VALUES (?, ?, ?, ?, ?, TRUE, ?)
          |ON CONFLICT DO NOTHING""".stripMargin
      ) { statement =>
        statement.setString(1, account.userId.value)
        statement.setString(2, account.handle.value)
        statement.setString(3, account.displayName.value)
        statement.setString(4, SkinId.wireValue(account.skinId))
        statement.setString(5, account.sessionToken.map(_.value).getOrElse(""))
        statement.setString(6, passwordHash.value)
        statement.executeUpdate()
      }
    }

    if (insertedRows == 1) {
      IdentityAccountCreateResult.Created(account)
    } else {
      IdentityAccountCreateResult.HandleAlreadyExists(findAnyByHandle(account.handle).getOrElse(account))
    }
  }

  override def replacePasswordHash(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE identity_accounts
          |SET password = ?
          |WHERE lower(handle) = lower(?) AND active = TRUE
          |RETURNING user_id, handle, display_name, skin_id, session_token""".stripMargin
      ) { statement =>
        statement.setString(1, passwordHash.value)
        statement.setString(2, handle.value.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readAccount(resultSet)) else None
        }
      }
    }

  override def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE identity_accounts
          |SET session_token = ?, active = TRUE
          |WHERE lower(handle) = lower(?)
          |RETURNING user_id, handle, display_name, skin_id, session_token""".stripMargin
      ) { statement =>
        statement.setString(1, sessionToken.value)
        statement.setString(2, handle.value.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readAccount(resultSet)) else None
        }
      }
    }

  override def listActiveAccounts(): Vector[IdentityAccount] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT user_id, handle, display_name, skin_id, session_token
          |FROM identity_accounts
          |WHERE active = TRUE
          |ORDER BY lower(handle) ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val accounts = Vector.newBuilder[IdentityAccount]
          while (resultSet.next()) {
            accounts += readAccount(resultSet)
          }
          accounts.result()
        }
      }
    }

  private def initialize(): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS identity_accounts (
          |  user_id TEXT PRIMARY KEY,
          |  handle TEXT NOT NULL UNIQUE,
          |  display_name TEXT NOT NULL,
          |  skin_id TEXT NOT NULL,
          |  session_token TEXT NOT NULL,
          |  active BOOLEAN NOT NULL,
          |  password TEXT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE UNIQUE INDEX IF NOT EXISTS identity_accounts_handle_lower_idx ON identity_accounts (lower(handle))"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS identity_accounts_session_token_idx ON identity_accounts (session_token)"
      )(_.executeUpdate())
    }

  private def findAnyByHandle(handle: PlayerHandle): Option[IdentityAccount] =
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token
        |FROM identity_accounts
        |WHERE lower(handle) = lower(?)""".stripMargin,
      statement => statement.setString(1, handle.value.trim)
    )

  private def queryOne(sql: String, bind: PreparedStatement => Unit): Option[IdentityAccount] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readAccount(resultSet)) else None
        }
      }
    }

  private def readAccount(resultSet: ResultSet): IdentityAccount =
    IdentityAccount(
      userId = UserId(resultSet.getString("user_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      skinId = SkinId.fromString(resultSet.getString("skin_id")).getOrElse(SkinId.Blue),
      sessionToken = SessionToken.fromString(resultSet.getString("session_token")),
      status = AccountStatus.Active
    )
}
