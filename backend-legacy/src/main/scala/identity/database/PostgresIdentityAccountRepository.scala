package slaydemo.backend.identity.database

import java.sql.{PreparedStatement, ResultSet}
import java.util.UUID

import slaydemo.backend.identity.objects.IdentityAccount
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.shared.objects.UserId

final class PostgresIdentityAccountRepository(config: PostgresConfig) extends IdentityAccountRepository {
  initialize()

  override def findByHandle(handle: String): Option[IdentityAccount] = {
    queryOne(
      "SELECT user_id, handle, display_name, skin_id, session_token, active FROM identity_accounts WHERE lower(handle) = lower(?)",
      statement => statement.setString(1, handle.trim)
    )
  }

  override def findBySessionToken(sessionToken: String): Option[IdentityAccount] = {
    queryOne(
      "SELECT user_id, handle, display_name, skin_id, session_token, active FROM identity_accounts WHERE session_token = ?",
      statement => statement.setString(1, sessionToken)
    )
  }

  override def listActiveAccounts(): Seq[IdentityAccount] = {
    queryMany(
      """SELECT user_id, handle, display_name, skin_id, session_token, active
        |FROM identity_accounts
        |WHERE active = TRUE
        |ORDER BY lower(handle) ASC""".stripMargin
    )
  }

  override def exists(handle: String): Boolean = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        "SELECT 1 FROM identity_accounts WHERE lower(handle) = lower(?) LIMIT 1"
      ) { statement =>
        statement.setString(1, handle.trim)
        PostgresSupport.withResultSet(statement)(_.next())
      }
    }
  }

  override def create(handle: String, password: String, skinId: String): IdentityAccount = {
    val account = IdentityAccount(
      userId = UserId(UUID.randomUUID().toString),
      handle = handle,
      displayName = handle,
      skinId = skinId,
      sessionToken = "",
      active = true
    )

    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO identity_accounts (
          |  user_id, handle, display_name, skin_id, session_token, active, password
          |) VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
      ) { statement =>
        statement.setString(1, account.userId.value)
        statement.setString(2, account.handle)
        statement.setString(3, account.displayName)
        statement.setString(4, account.skinId)
        statement.setString(5, account.sessionToken)
        statement.setBoolean(6, account.active)
        statement.setString(7, password)
        statement.executeUpdate()
      }
    }

    account
  }

  override def authenticate(handle: String, password: String): Option[IdentityAccount] = {
    queryOne(
      """SELECT user_id, handle, display_name, skin_id, session_token, active
        |FROM identity_accounts
        |WHERE lower(handle) = lower(?) AND password = ?""".stripMargin,
      statement => {
        statement.setString(1, handle.trim)
        statement.setString(2, password)
      }
    )
  }

  override def updateSession(handle: String, sessionToken: String): Option[IdentityAccount] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """UPDATE identity_accounts
          |SET session_token = ?, active = TRUE
          |WHERE lower(handle) = lower(?)
          |RETURNING user_id, handle, display_name, skin_id, session_token, active""".stripMargin
      ) { statement =>
        statement.setString(1, sessionToken)
        statement.setString(2, handle.trim)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readAccount(resultSet)) else None
        }
      }
    }
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
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
  }

  private def queryOne(sql: String, bind: PreparedStatement => Unit): Option[IdentityAccount] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readAccount(resultSet)) else None
        }
      }
    }
  }

  private def queryMany(sql: String): Seq[IdentityAccount] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val accounts = Vector.newBuilder[IdentityAccount]
          while (resultSet.next()) {
            accounts += readAccount(resultSet)
          }
          accounts.result()
        }
      }
    }
  }

  private def readAccount(resultSet: ResultSet): IdentityAccount = {
    IdentityAccount(
      userId = UserId(resultSet.getString("user_id")),
      handle = resultSet.getString("handle"),
      displayName = resultSet.getString("display_name"),
      skinId = resultSet.getString("skin_id"),
      sessionToken = resultSet.getString("session_token"),
      active = resultSet.getBoolean("active")
    )
  }
}
