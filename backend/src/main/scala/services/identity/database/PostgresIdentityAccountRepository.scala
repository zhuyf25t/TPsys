package services.identity.database

import java.sql.PreparedStatement

import services.identity.objects.{
  IdentityAccount,
  PasswordHash,
  PlainTextPassword,
  PlayerHandle,
  SessionToken
}
import services.identity.database.PostgresIdentityAccountRecordMapper.{bindCreate, readAccounts, readOptionalAccount}
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

final class PostgresIdentityAccountRepository(settings: PostgresConnectionSettings) extends IdentityAccountRepository {
  PostgresIdentityAccountSchema.initialize(settings)

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

  override def findPasswordHashByHandle(handle: PlayerHandle): Option[PasswordHash] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT password
          |FROM identity_accounts
          |WHERE lower(handle) = lower(?) AND active = TRUE
          |LIMIT 1""".stripMargin
      ) { statement =>
        statement.setString(1, handle.value.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then PasswordHash.fromString(resultSet.getString("password"))
          else None
        }
      }
    }

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
        |  AND password NOT LIKE '$pbkdf2-sha256$%'
        |  AND active = TRUE""".stripMargin,
      statement => {
        statement.setString(1, handle.value.trim)
        statement.setString(2, password.value)
      }
    )

  override def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult = {
    val insertedRows = PostgresSupport.withTransactionConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO identity_accounts (
          |  user_id, handle, display_name, skin_id, session_token, active, password
          |) VALUES (?, ?, ?, ?, ?, TRUE, ?)
          |ON CONFLICT DO NOTHING""".stripMargin
      ) { statement =>
        bindCreate(statement, account, passwordHash)
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
    PostgresSupport.withTransactionConnection(settings) { connection =>
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
          readOptionalAccount(resultSet)
        }
      }
    }

  override def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount] =
    PostgresSupport.withTransactionConnection(settings) { connection =>
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
          readOptionalAccount(resultSet)
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
        PostgresSupport.withResultSet(statement)(readAccounts)
      }
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
          readOptionalAccount(resultSet)
        }
      }
    }

}
