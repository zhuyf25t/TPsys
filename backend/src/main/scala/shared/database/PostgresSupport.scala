package slaydemo.backend.shared.database

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.Properties

final case class PostgresConfig(
  jdbcUrl: String,
  user: Option[String],
  password: Option[String]
)

object PostgresSupport {
  def configFromEnvironment(env: Map[String, String] = sys.env): Option[PostgresConfig] = {
    val rawUrl = env
      .get("SLAY_DEMO_DATABASE_URL")
      .orElse(env.get("DATABASE_URL"))
      .map(_.trim)
      .filter(_.nonEmpty)

    rawUrl.flatMap { value =>
      parseDatabaseUrl(value).map { parsed =>
        parsed.copy(
          user = env.get("SLAY_DEMO_DATABASE_USER").filter(_.nonEmpty).orElse(parsed.user),
          password = env.get("SLAY_DEMO_DATABASE_PASSWORD").filter(_.nonEmpty).orElse(parsed.password)
        )
      }
    }
  }

  def connect(config: PostgresConfig): Connection = {
    Class.forName("org.postgresql.Driver")

    val properties = new Properties()
    config.user.foreach(properties.setProperty("user", _))
    config.password.foreach(properties.setProperty("password", _))

    DriverManager.getConnection(config.jdbcUrl, properties)
  }

  def withConnection[A](config: PostgresConfig)(use: Connection => A): A = {
    val connection = connect(config)
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

  private def parseDatabaseUrl(rawUrl: String): Option[PostgresConfig] = {
    if (rawUrl.startsWith("jdbc:postgresql:")) {
      Some(PostgresConfig(rawUrl, None, None))
    } else if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
      parsePostgresUrl(rawUrl)
    } else {
      None
    }
  }

  private def parsePostgresUrl(rawUrl: String): Option[PostgresConfig] = {
    try {
      val uri = URI(rawUrl)
      val host = Option(uri.getHost).filter(_.nonEmpty)
      val path = Option(uri.getRawPath).filter(_.nonEmpty).getOrElse("/")
      val query = Option(uri.getRawQuery).filter(_.nonEmpty).map(value => s"?$value").getOrElse("")
      val port = if (uri.getPort > 0) s":${uri.getPort}" else ""

      host.map { value =>
        val credentials = Option(uri.getRawUserInfo)
          .map(_.split(":", 2).toList)
          .map {
            case rawUser :: rawPassword :: Nil => Some(decode(rawUser)) -> Some(decode(rawPassword))
            case rawUser :: Nil               => Some(decode(rawUser)) -> None
            case _                            => None -> None
          }
          .getOrElse(None -> None)

        PostgresConfig(
          jdbcUrl = s"jdbc:postgresql://$value$port$path$query",
          user = credentials._1,
          password = credentials._2
        )
      }
    } catch {
      case _: IllegalArgumentException => None
    }
  }

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}
