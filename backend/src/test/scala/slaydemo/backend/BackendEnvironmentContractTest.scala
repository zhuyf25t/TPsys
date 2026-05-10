package slaydemo.backend

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.shared.storage.{StorageConfig, JdbcUrl}

object BackendEnvironmentContractTest {
  def main(args: Array[String]): Unit = {
    parsesDotEnvAssignments()
    loadsParentAndCurrentLocalEnvFiles()
    processEnvironmentOverridesLocalFiles()
    loadedLocalPostgresSettingsBuildTypedConfig()

    println("BackendEnvironment contract checks passed")
  }

  private def parsesDotEnvAssignments(): Unit = {
    val parsed = BackendEnvironment.parse(
      """
        |# ignored comment
        |export SLAY_DEMO_STORAGE_MODE=postgres
        |SLAY_DEMO_DATABASE_URL="jdbc:postgresql://localhost:5432/slay_demo"
        |SLAY_DEMO_DATABASE_PASSWORD='value=with=equals'
        |1_INVALID=ignored
        |MISSING_EQUALS
        |""".stripMargin
    )

    assertEquals("storage mode parsed", parsed.get("SLAY_DEMO_STORAGE_MODE"), Some("postgres"))
    assertEquals(
      "quoted jdbc url parsed",
      parsed.get("SLAY_DEMO_DATABASE_URL"),
      Some("jdbc:postgresql://localhost:5432/slay_demo")
    )
    assertEquals("values may contain equals", parsed.get("SLAY_DEMO_DATABASE_PASSWORD"), Some("value=with=equals"))
    assertEquals("invalid key ignored", parsed.contains("1_INVALID"), false)
  }

  private def loadsParentAndCurrentLocalEnvFiles(): Unit =
    withTemporaryDirectories { (root, backendDir) =>
      write(root.resolve(".env.local"), "SLAY_DEMO_STORAGE_MODE=postgres\n")
      write(backendDir.resolve(".env.local"), "SLAY_DEMO_BACKEND_PORT=18080\n")

      val loaded = BackendEnvironment.load(processEnv = Map.empty, currentDirectory = backendDir)

      assertEquals("parent .env.local loaded", loaded.get("SLAY_DEMO_STORAGE_MODE"), Some("postgres"))
      assertEquals("current .env.local loaded", loaded.get("SLAY_DEMO_BACKEND_PORT"), Some("18080"))
    }

  private def processEnvironmentOverridesLocalFiles(): Unit =
    withTemporaryDirectories { (root, backendDir) =>
      write(root.resolve(".env.local"), "SLAY_DEMO_STORAGE_MODE=memory\nSLAY_DEMO_DATABASE_URL=from-file\n")

      val loaded = BackendEnvironment.load(
        processEnv = Map(
          "SLAY_DEMO_STORAGE_MODE" -> "postgres",
          "SLAY_DEMO_DATABASE_URL" -> "from-process"
        ),
        currentDirectory = backendDir
      )

      assertEquals("process storage mode wins", loaded.get("SLAY_DEMO_STORAGE_MODE"), Some("postgres"))
      assertEquals("process database url wins", loaded.get("SLAY_DEMO_DATABASE_URL"), Some("from-process"))
    }

  private def loadedLocalPostgresSettingsBuildTypedConfig(): Unit =
    withTemporaryDirectories { (root, backendDir) =>
      write(
        root.resolve(".env.local"),
        "SLAY_DEMO_STORAGE_MODE=postgres\nSLAY_DEMO_DATABASE_URL=jdbc:postgresql://localhost:5432/slay_demo\n"
      )

      BackendConfig.fromEnvironment(BackendEnvironment.load(processEnv = Map.empty, currentDirectory = backendDir)) match {
        case Right(config) =>
          config.storage match {
            case StorageConfig.Postgres(connection) =>
              assertEquals("typed jdbc url loaded", connection.jdbcUrl, JdbcUrl("jdbc:postgresql://localhost:5432/slay_demo"))
            case other =>
              fail(s"expected postgres storage, got $other")
          }
        case Left(error) =>
          fail(s"expected backend config, got $error")
      }
    }

  private def withTemporaryDirectories(check: (Path, Path) => Unit): Unit = {
    val root = Files.createTempDirectory("slay-demo-env-contract")
    val backendDir = Files.createDirectories(root.resolve("backend"))
    try {
      check(root, backendDir)
    } finally {
      deleteRecursively(root)
    }
  }

  private def write(path: Path, content: String): Unit =
    Files.writeString(path, content, StandardCharsets.UTF_8)

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path) then {
      if Files.isDirectory(path) then {
        val children = Files.list(path)
        try {
          children.forEach(deleteRecursively)
        } finally {
          children.close()
        }
      }
      Files.deleteIfExists(path)
    }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
