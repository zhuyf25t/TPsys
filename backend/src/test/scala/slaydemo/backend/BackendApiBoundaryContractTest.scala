package slaydemo.backend

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object BackendApiBoundaryContractTest {
  private val SourceRoot: Path =
    Paths.get("src", "main", "scala", "slaydemo", "backend")

  private val Http4sRoot: Path =
    SourceRoot.resolve("http4s")

  private val DeletedApiMessageBoundaryFiles: Vector[Path] =
    Vector(
      SourceRoot.resolve(Paths.get("shared", "api", "BackendAPIMessage.scala")),
      SourceRoot.resolve(Paths.get("shared", "api", "BackendIO.scala"))
    )

  private val ForbiddenSourceFragments: Vector[String] =
    Vector(
      "BackendAPIExchangeRouter",
      "apiEndpoints",
      "ApiMessageRouteContexts",
      "BackendAPIEndpoint",
      "BackendAPIMessagePlanner",
      "BackendIO",
      "RouteErrorMapper",
      "RouteTargetParsers",
      "jsonTextResponse",
      "DriverManager"
    )

  private val ForbiddenHttp4sRouteFragments: Vector[String] =
    Vector(
      "apiError(status =",
      "apiError(Status."
    )

  private val ForbiddenLegacyHttpServerFragments: Vector[String] =
    Vector(
      "com.sun.net.httpserver",
      "HttpExchange"
    )

  def main(args: Array[String]): Unit = {
    deletedApiMessageBoundaryFilesStayDeleted()
    oldApiMessageBoundaryNamesStayOutOfMainSources()
    legacyRouteJsonRenderersStayDeleted()
    legacyHttpExchangeAdaptersStayDeleted()
    legacyHttpServerTypesStayOutOfMainSources()
    http4sBoundaryFilesUseTypedApiErrors()
    http4sRoutesDoNotImportDomainRoutes()

    println("Backend API boundary contract checks passed")
  }

  private def deletedApiMessageBoundaryFilesStayDeleted(): Unit = {
    val existingFiles = DeletedApiMessageBoundaryFiles.filter(Files.exists(_))

    assert(
      existingFiles.isEmpty,
      s"deleted APIMessage boundary artifacts must not be recreated: ${existingFiles.mkString(", ")}"
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

  private def legacyHttpExchangeAdaptersStayDeleted(): Unit = {
    val violations = scalaFiles(SourceRoot)
      .filter(path => SourceRoot.relativize(path).iterator().asScala.exists(_.toString == "routes"))
      .map(path => SourceRoot.relativize(path).toString)

    assert(
      violations.isEmpty,
      s"legacy HttpExchange route adapter packages must stay deleted from main sources:\n${violations.mkString("\n")}"
    )
  }

  private def legacyHttpServerTypesStayOutOfMainSources(): Unit = {
    val violations = for {
      file <- scalaFiles(SourceRoot)
      source = Files.readString(file)
      forbidden <- ForbiddenLegacyHttpServerFragments
      if source.contains(forbidden)
    } yield s"${SourceRoot.relativize(file)} contains forbidden legacy server fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"legacy Java HttpServer boundary types must stay out of main sources:\n${violations.mkString("\n")}"
    )
  }

  private def http4sBoundaryFilesUseTypedApiErrors(): Unit = {
    val http4sFiles = scalaFiles(Http4sRoot)
    val violations = for {
      file <- http4sFiles
      source = Files.readString(file)
      forbidden <- ForbiddenHttp4sRouteFragments
      if source.contains(forbidden)
    } yield s"${Http4sRoot.relativize(file)} contains forbidden fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"http4s boundary files must build typed HttpApiError values before rendering errors:\n${violations.mkString("\n")}"
    )
  }

  private def http4sRoutesDoNotImportDomainRoutes(): Unit = {
    val violations = for {
      file <- scalaFiles(Http4sRoot)
      line <- Files.readAllLines(file).asScala.map(_.trim)
      if line.startsWith("import slaydemo.backend.") && line.contains(".routes")
    } yield s"${Http4sRoot.relativize(file)} imports legacy route adapter package: `$line`"

    assert(
      violations.isEmpty,
      s"http4s routes must depend on apiTypes/services, not legacy HttpExchange route packages:\n${violations.mkString("\n")}"
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
