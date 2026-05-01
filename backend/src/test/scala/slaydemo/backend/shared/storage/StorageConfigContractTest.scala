package slaydemo.backend.shared.storage

object StorageConfigContractTest {
  def main(args: Array[String]): Unit = {
    defaultStorageIsMemory()
    genericDatabaseUrlDoesNotEnablePostgres()
    postgresModeRequiresExplicitJdbcUrl()
    postgresModeReadsExplicitConnectionSettings()
    fileModeRequiresStorageRoot()
    fileModeReadsStorageRoot()
    unsupportedModeReportsNormalizedValue()

    println("StorageConfig contract checks passed")
  }

  private def defaultStorageIsMemory(): Unit =
    assertEquals(
      "empty environment defaults to in-memory storage",
      StorageConfig.fromEnvironment(Map.empty),
      Right(StorageConfig.InMemory)
    )

  private def genericDatabaseUrlDoesNotEnablePostgres(): Unit =
    assertEquals(
      "generic DATABASE_URL is ignored unless SLAY_DEMO_STORAGE_MODE opts in",
      StorageConfig.fromEnvironment(
        Map("DATABASE_URL" -> "jdbc:postgresql://localhost:5432/slay_demo")
      ),
      Right(StorageConfig.InMemory)
    )

  private def postgresModeRequiresExplicitJdbcUrl(): Unit =
    assertEquals(
      "postgres mode requires SLAY_DEMO_DATABASE_URL",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> "postgres")),
      Left(StorageConfigError.MissingPostgresJdbcUrl)
    )

  private def postgresModeReadsExplicitConnectionSettings(): Unit = {
    val actual = StorageConfig.fromEnvironment(
      Map(
        "SLAY_DEMO_STORAGE_MODE" -> "postgres",
        "SLAY_DEMO_DATABASE_URL" -> " jdbc:postgresql://localhost:5432/slay_demo ",
        "SLAY_DEMO_DATABASE_USER" -> " slay_user ",
        "SLAY_DEMO_DATABASE_PASSWORD" -> " super-secret "
      )
    )

    actual match {
      case Right(StorageConfig.Postgres(connection)) =>
        assertEquals(
          "postgres jdbc url is trimmed",
          connection.jdbcUrl,
          JdbcUrl("jdbc:postgresql://localhost:5432/slay_demo")
        )
        assertEquals("postgres user is trimmed", connection.user, Some(DatabaseUser("slay_user")))
        assert(
          connection.password.exists(_.toString == "<redacted>"),
          "postgres password must render as redacted"
        )
      case other =>
        fail(s"expected postgres config, got $other")
    }
  }

  private def fileModeRequiresStorageRoot(): Unit =
    assertEquals(
      "file mode requires SLAY_DEMO_DATA_DIR",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> "file")),
      Left(StorageConfigError.MissingFileStorageRoot)
    )

  private def fileModeReadsStorageRoot(): Unit =
    assertEquals(
      "file mode reads and trims SLAY_DEMO_DATA_DIR",
      StorageConfig.fromEnvironment(
        Map("SLAY_DEMO_STORAGE_MODE" -> "files", "SLAY_DEMO_DATA_DIR" -> " ./data ")
      ),
      Right(StorageConfig.File(StorageRoot("./data")))
    )

  private def unsupportedModeReportsNormalizedValue(): Unit =
    assertEquals(
      "unsupported storage mode is reported after normalization",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> " Redis ")),
      Left(StorageConfigError.UnsupportedStorageMode("redis"))
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
