package slaydemo.backend.shared.storage

enum StorageConfigError {
  case UnsupportedStorageMode(rawValue: String)
  case MissingFileStorageRoot
  case MissingPostgresJdbcUrl

  def message: String =
    this match {
      case StorageConfigError.UnsupportedStorageMode(rawValue) =>
        s"Unsupported SLAY_DEMO_STORAGE_MODE: $rawValue"
      case StorageConfigError.MissingFileStorageRoot =>
        "SLAY_DEMO_STORAGE_MODE=file requires SLAY_DEMO_DATA_DIR"
      case StorageConfigError.MissingPostgresJdbcUrl =>
        "SLAY_DEMO_STORAGE_MODE=postgres requires SLAY_DEMO_DATABASE_URL"
    }
}

enum StorageMode {
  case Memory
  case File
  case Postgres
}

object StorageMode {
  def fromEnvironmentValue(value: String): Either[StorageConfigError, StorageMode] =
    value.trim.toLowerCase match {
      case "" | "memory" | "in-memory" | "inmemory" => Right(StorageMode.Memory)
      case "file" | "files"                         => Right(StorageMode.File)
      case "postgres" | "postgresql"                => Right(StorageMode.Postgres)
      case other                                    => Left(StorageConfigError.UnsupportedStorageMode(other))
    }

  def wireValue(mode: StorageMode): String =
    mode match {
      case StorageMode.Memory   => "memory"
      case StorageMode.File     => "file"
      case StorageMode.Postgres => "postgres"
    }
}

sealed trait StorageConfig {
  def mode: StorageMode
}

object StorageConfig {
  case object InMemory extends StorageConfig {
    override val mode: StorageMode = StorageMode.Memory
  }

  final case class File(root: StorageRoot) extends StorageConfig {
    override val mode: StorageMode = StorageMode.File
  }

  final case class Postgres(connection: PostgresConnectionSettings) extends StorageConfig {
    override val mode: StorageMode = StorageMode.Postgres
  }

  def fromEnvironment(env: Map[String, String]): Either[StorageConfigError, StorageConfig] = {
    val storageMode = env
      .get("SLAY_DEMO_STORAGE_MODE")
      .map(StorageMode.fromEnvironmentValue)
      .getOrElse(Right(StorageMode.Memory))

    storageMode.flatMap {
      case StorageMode.Memory =>
        Right(InMemory)
      case StorageMode.File =>
        env
          .get("SLAY_DEMO_DATA_DIR")
          .flatMap(StorageRoot.fromString)
          .map(root => Right(File(root)))
          .getOrElse(Left(StorageConfigError.MissingFileStorageRoot))
      case StorageMode.Postgres =>
        env
          .get("SLAY_DEMO_DATABASE_URL")
          .flatMap(JdbcUrl.fromString)
          .map { jdbcUrl =>
            Right(
              Postgres(
                PostgresConnectionSettings(
                  jdbcUrl = jdbcUrl,
                  user = env.get("SLAY_DEMO_DATABASE_USER").flatMap(DatabaseUser.fromString),
                  password = env.get("SLAY_DEMO_DATABASE_PASSWORD").flatMap(DatabasePassword.fromString)
                )
              )
            )
          }
          .getOrElse(Left(StorageConfigError.MissingPostgresJdbcUrl))
    }
  }
}

final case class StorageRoot(value: String) extends AnyVal

object StorageRoot {
  def fromString(value: String): Option[StorageRoot] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(StorageRoot.apply)
}

final case class JdbcUrl(value: String) extends AnyVal

object JdbcUrl {
  def fromString(value: String): Option[JdbcUrl] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(JdbcUrl.apply)
}

final case class DatabaseUser(value: String) extends AnyVal

object DatabaseUser {
  def fromString(value: String): Option[DatabaseUser] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(DatabaseUser.apply)
}

final case class PostgresConnectionSettings(
  jdbcUrl: JdbcUrl,
  user: Option[DatabaseUser],
  password: Option[DatabasePassword]
)

final class DatabasePassword private (val value: String) {
  override def toString: String = "<redacted>"
}

object DatabasePassword {
  def fromString(value: String): Option[DatabasePassword] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(new DatabasePassword(_))
}
