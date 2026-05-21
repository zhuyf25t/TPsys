package slaydemo.backend.shared.database

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object PostgresRepositoryBoundaryContractTest {
  private val SourceRoot: Path =
    Paths.get("src", "main", "scala", "slaydemo", "backend")

  private val ForbiddenRepositoryFragments: Vector[String] =
    Vector(
      "PostgresSupport.connect(",
      "PostgresSupport.withTransaction(",
      ".setAutoCommit(",
      ".commit(",
      ".rollback("
    )

  def main(args: Array[String]): Unit = {
    postgresRepositoriesUseSharedConnectionBoundaries()
    postgresRepositoryWritesUseTransactionBoundary()

    println("Postgres repository boundary contract checks passed")
  }

  private def postgresRepositoriesUseSharedConnectionBoundaries(): Unit = {
    assert(Files.exists(SourceRoot), s"source root does not exist: $SourceRoot")

    val repositories = postgresRepositoryFiles()
    assert(repositories.nonEmpty, s"no postgres repository files found under $SourceRoot")

    val violations = for {
      file <- repositories
      source = Files.readString(file)
      forbidden <- ForbiddenRepositoryFragments
      if source.contains(forbidden)
    } yield s"${SourceRoot.relativize(file)} contains forbidden fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"Postgres repositories must use PostgresSupport shared connection/transaction helpers:\n${violations.mkString("\n")}"
    )
  }

  private def postgresRepositoryWritesUseTransactionBoundary(): Unit = {
    val violations = for {
      file <- postgresRepositoryFiles()
      lines = Files.readString(file).linesIterator.toVector
      (line, index) <- lines.zipWithIndex
      if isRepositoryWriteLine(line)
      boundary = nearestConnectionBoundary(lines, index)
      if !boundary.exists(_.contains("PostgresSupport.withTransactionConnection(settings)"))
    } yield {
      val renderedBoundary = boundary.getOrElse("<no connection boundary>")
      s"${SourceRoot.relativize(file)}:${index + 1} writes outside transaction boundary; nearest boundary: $renderedBoundary"
    }

    assert(
      violations.isEmpty,
      s"Postgres repository writes must use PostgresSupport.withTransactionConnection:\n${violations.mkString("\n")}"
    )
  }

  private def isRepositoryWriteLine(line: String): Boolean = {
    val normalized = line.trim
    normalized.contains("executeUpdate(") ||
    normalized.contains("\"\"\"INSERT INTO ") ||
    normalized.contains("|INSERT INTO ") ||
    normalized.contains("\"\"\"UPDATE ") ||
    normalized.contains("|UPDATE ")
  }

  private def nearestConnectionBoundary(lines: Vector[String], index: Int): Option[String] =
    lines
      .take(index + 1)
      .reverse
      .find(line =>
        line.contains("PostgresSupport.withTransactionConnection(settings)") ||
          line.contains("PostgresSupport.withConnection(settings)")
      )
      .map(_.trim)

  private def postgresRepositoryFiles(): Vector[Path] = {
    val stream = Files.walk(SourceRoot)
    try {
      stream
        .iterator()
        .asScala
        .toVector
        .filter(path => Files.isRegularFile(path))
        .filter(path => path.getFileName.toString.endsWith("Repository.scala"))
        .filter(path => path.getFileName.toString.contains("Postgres"))
        .sortBy(_.toString)
    } finally {
      stream.close()
    }
  }
}
