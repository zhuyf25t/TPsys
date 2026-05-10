package slaydemo.backend.shared.database

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.Properties
import scala.util.control.NonFatal

import slaydemo.backend.shared.storage.PostgresConnectionSettings

object PostgresSupport {
  def connect(settings: PostgresConnectionSettings): Connection = {
    Class.forName("org.postgresql.Driver")

    val properties = Properties()
    settings.user.foreach(user => properties.setProperty("user", user.value))
    settings.password.foreach(password => properties.setProperty("password", password.value))

    DriverManager.getConnection(settings.jdbcUrl.value, properties)
  }

  def withConnection[A](settings: PostgresConnectionSettings)(use: Connection => A): A = {
    val connection = connect(settings)
    try use(connection)
    finally connection.close()
  }

  def withStatement[A](connection: Connection, sql: String)(use: PreparedStatement => A): A = {
    val statement = connection.prepareStatement(sql)
    try use(statement)
    finally statement.close()
  }

  def withResultSet[A](statement: PreparedStatement)(use: ResultSet => A): A = {
    val resultSet = statement.executeQuery()
    try use(resultSet)
    finally resultSet.close()
  }

  def withTransaction[A](connection: Connection)(body: => A): A = {
    val previousAutoCommit = connection.getAutoCommit
    connection.setAutoCommit(false)
    try {
      val result = body
      connection.commit()
      result
    } catch {
      case NonFatal(error) =>
        connection.rollback()
        throw error
    } finally {
      connection.setAutoCommit(previousAutoCommit)
    }
  }
}
