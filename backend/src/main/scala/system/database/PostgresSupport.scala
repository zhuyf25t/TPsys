package system.database

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import system.storage.PostgresConnectionSettings

object PostgresSupport {
  private val PoolMaximumSize: Int = 10
  private val pools = ConcurrentHashMap[PostgresConnectionPoolKey, HikariDataSource]()

  def connect(settings: PostgresConnectionSettings): Connection =
    dataSource(settings).getConnection

  def connectionResource(settings: PostgresConnectionSettings): Resource[IO, Connection] =
    connectionResource(IO.blocking(connect(settings)))

  def connectionResource(acquire: IO[Connection]): Resource[IO, Connection] =
    Resource.make(acquire)(connection => IO.blocking(connection.close()))

  def withConnectionIO[A](settings: PostgresConnectionSettings)(use: Connection => IO[A]): IO[A] =
    connectionResource(settings).use(use)

  def withTransactionIO[A](connection: Connection)(body: IO[A]): IO[A] =
    for {
      previousAutoCommit <- IO.blocking(connection.getAutoCommit)
      _ <- IO.blocking(connection.setAutoCommit(false))
      result <- body.attempt
        .flatMap {
          case Right(value) =>
            IO.blocking(connection.commit()).as(value)
          case Left(error) =>
            IO.blocking(connection.rollback()) *> IO.raiseError[A](error)
        }
        .guarantee(IO.blocking(connection.setAutoCommit(previousAutoCommit)))
    } yield result

  def dataSource(settings: PostgresConnectionSettings): HikariDataSource = {
    Class.forName("org.postgresql.Driver")

    val key = PostgresConnectionPoolKey.from(settings)
    pools.computeIfAbsent(key, _ => new HikariDataSource(buildHikariConfig(settings, key)))
  }

  def buildHikariConfig(settings: PostgresConnectionSettings): HikariConfig =
    buildHikariConfig(settings, PostgresConnectionPoolKey.from(settings))

  private def buildHikariConfig(settings: PostgresConnectionSettings, key: PostgresConnectionPoolKey): HikariConfig = {
    val config = HikariConfig()
    config.setJdbcUrl(settings.jdbcUrl.value)
    settings.user.foreach(user => config.setUsername(user.value))
    settings.password.foreach(password => config.setPassword(password.value))
    config.setMaximumPoolSize(PoolMaximumSize)
    config.setPoolName(s"slay-demo-postgres-${Integer.toUnsignedString(key.hashCode())}")
    config
  }

  def withConnection[A](settings: PostgresConnectionSettings)(use: Connection => A): A = {
    val connection = connect(settings)
    try use(connection)
    finally connection.close()
  }

  def withTransactionConnection[A](settings: PostgresConnectionSettings)(use: Connection => A): A =
    withTransactionConnection(connect(settings))(use)

  def withTransactionConnection[A](acquire: => Connection)(use: Connection => A): A = {
    val connection = acquire
    try withTransaction(connection)(use(connection))
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

  def closeAll(): Unit = {
    pools.values().asScala.foreach(_.close())
    pools.clear()
  }
}

private[database] final case class PostgresConnectionPoolKey(
  jdbcUrl: String,
  user: Option[String],
  password: Option[String]
)

private[database] object PostgresConnectionPoolKey {
  def from(settings: PostgresConnectionSettings): PostgresConnectionPoolKey =
    PostgresConnectionPoolKey(
      jdbcUrl = settings.jdbcUrl.value,
      user = settings.user.map(_.value),
      password = settings.password.map(_.value)
    )
}
