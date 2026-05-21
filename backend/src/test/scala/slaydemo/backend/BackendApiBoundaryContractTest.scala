package slaydemo.backend

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object BackendApiBoundaryContractTest {
  private val SourceRoot: Path =
    Paths.get("src", "main", "scala", "slaydemo", "backend")

  private val Http4sRoot: Path =
    SourceRoot.resolve("http4s")

  private val DeletedBoundaryFiles: Vector[Path] =
    Vector(
      SourceRoot.resolve(Paths.get("shared", "api", "BackendAPIMessage.scala")),
      SourceRoot.resolve(Paths.get("shared", "api", "BackendIO.scala")),
      SourceRoot.resolve(Paths.get("shared", "routes", "HealthAPIMessagePlanner.scala")),
      SourceRoot.resolve(Paths.get("replay", "routes", "ReplayCatalogAPIMessagePlanner.scala"))
    )

  private val DeletedBoundaryDirectoriesWithNoScalaFiles: Vector[Path] =
    Vector(SourceRoot.resolve(Paths.get("battle", "routes", "api")))

  private val ForbiddenSourceFragments: Vector[String] =
    Vector(
      "BackendAPIExchangeRouter",
      "apiEndpoints",
      "ApiMessageRouteContexts",
      "BackendAPIEndpoint",
      "BackendAPIMessagePlanner",
      "BackendIO",
      "jsonTextResponse",
      "DriverManager"
    )

  private val ForbiddenHttp4sRouteFragments: Vector[String] =
    Vector(
      "apiError(status =",
      "apiError(Status."
    )

  def main(args: Array[String]): Unit = {
    deletedApiMessageBoundaryFilesStayDeleted()
    oldApiMessageBoundaryNamesStayOutOfMainSources()
    legacyRouteJsonRenderersStayDeleted()
    http4sRoutesUseTypedApiErrors()

    println("Backend API boundary contract checks passed")
  }

  private def deletedApiMessageBoundaryFilesStayDeleted(): Unit = {
    val existingFiles = DeletedBoundaryFiles.filter(Files.exists(_))
    val deletedDirectorySources = DeletedBoundaryDirectoriesWithNoScalaFiles
      .filter(Files.exists(_))
      .flatMap(scalaFiles)

    assert(
      existingFiles.isEmpty && deletedDirectorySources.isEmpty,
      s"deleted APIMessage boundary artifacts must not be recreated: ${(existingFiles ++ deletedDirectorySources).mkString(", ")}"
    )
  }

  private def oldApiMessageBoundaryNamesStayOutOfMainSources(): Unit = {
    val violations = for {
      file <- scalaFiles(SourceRoot)
      source = Files.readString(file)
      forbidden <- ForbiddenSourceFragments
      if source.contains(forbidden)
    } yield s"${SourceRoot.relativize(file)} contains forbidden fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"old APIMessage/backend boundary fragments must stay removed:\n${violations.mkString("\n")}"
    )
  }

  private def legacyRouteJsonRenderersStayDeleted(): Unit = {
    val violations = scalaFiles(SourceRoot)
      .filter(path => path.getFileName.toString.endsWith("RouteJsonRenderer.scala"))
      .map(path => SourceRoot.relativize(path).toString)

    assert(
      violations.isEmpty,
      s"legacy route-specific JSON renderers must stay replaced by shared apiTypes codecs:\n${violations.mkString("\n")}"
    )
  }

  private def http4sRoutesUseTypedApiErrors(): Unit = {
    val routeFiles = scalaFiles(Http4sRoot).filterNot(_.getFileName.toString == "Http4sRouteSupport.scala")
    val violations = for {
      file <- routeFiles
      source = Files.readString(file)
      forbidden <- ForbiddenHttp4sRouteFragments
      if source.contains(forbidden)
    } yield s"${Http4sRoot.relativize(file)} contains forbidden fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"http4s routes must build typed HttpApiError values before rendering errors:\n${violations.mkString("\n")}"
    )
  }

  private def scalaFiles(root: Path): Vector[Path] = {
    assert(Files.exists(root), s"source root does not exist: $root")

    val stream = Files.walk(root)
    try {
      stream
        .iterator()
        .asScala
        .toVector
        .filter(path => Files.isRegularFile(path))
        .filter(path => path.getFileName.toString.endsWith(".scala"))
        .sortBy(_.toString)
    } finally {
      stream.close()
    }
  }
}
