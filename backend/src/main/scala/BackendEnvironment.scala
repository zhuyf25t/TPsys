package services

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

object BackendEnvironment {
  private val EnvFileNames: Vector[String] =
    Vector(".env", ".env.local")

  def load(processEnv: Map[String, String] = sys.env, currentDirectory: Path = Paths.get("")): Map[String, String] = {
    val fileEnv = candidateEnvFiles(currentDirectory)
      .filter(Files.isRegularFile(_))
      .foldLeft(Map.empty[String, String]) { (loaded, path) =>
        loaded ++ parse(Files.readString(path, StandardCharsets.UTF_8))
      }

    fileEnv ++ processEnv
  }

  private[services] def candidateEnvFiles(currentDirectory: Path): Vector[Path] = {
    val normalized = currentDirectory.toAbsolutePath.normalize
    val directories = Vector(Option(normalized.getParent), Some(normalized)).flatten.distinct

    directories
      .flatMap(directory => EnvFileNames.map(fileName => directory.resolve(fileName).normalize))
      .distinct
  }

  private[services] def parse(content: String): Map[String, String] =
    content
      .linesIterator
      .flatMap(parseLine)
      .toMap

  private def parseLine(rawLine: String): Option[(String, String)] = {
    val line = rawLine.trim
    if line.isEmpty || line.startsWith("#") then {
      None
    } else {
      val assignment = if line.startsWith("export ") then line.drop("export ".length).trim else line
      val equalsIndex = assignment.indexOf("=")
      if equalsIndex <= 0 then {
        None
      } else {
        val key = assignment.substring(0, equalsIndex).trim
        val rawValue = assignment.substring(equalsIndex + 1).trim
        if isEnvironmentKey(key) then Some(key -> unquote(rawValue)) else None
      }
    }
  }

  private def isEnvironmentKey(value: String): Boolean =
    value.matches("[A-Za-z_][A-Za-z0-9_]*")

  private def unquote(value: String): String =
    if value.length >= 2 && value.head == '"' && value.last == '"' then {
      value.substring(1, value.length - 1)
    } else if value.length >= 2 && value.head == '\'' && value.last == '\'' then {
      value.substring(1, value.length - 1)
    } else {
      value
    }
}
