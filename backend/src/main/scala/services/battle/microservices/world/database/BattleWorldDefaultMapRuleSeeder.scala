package services.battle.microservices.world.database

import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
import java.time.Instant

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode

import services.battle.microservices.world.objects.world.BattleWorldMapSpecJson
import services.battle.objects.core.{BattleMapId, BattleVector2}

private[services] object BattleWorldDefaultMapRuleSeeder {
  private val MapFileNames: Vector[String] =
    Vector(
      "default-industrial-arena.json",
      "fall-hunt-v1.json",
      "winter-hunt-v1.json",
      "normal-hunt-v1.json"
    )

  def upsertDefaultMaps(connection: Connection): IO[Unit] =
    defaultMapDirectory.flatMap { directory =>
      MapFileNames.traverse_(fileName => upsertMapFile(connection, directory.resolve(fileName), Instant.now()))
    }

  private def defaultMapDirectory: IO[Path] =
    IO.blocking {
      val backendRelative = Paths.get("..", "shared", "battle", "maps").toAbsolutePath.normalize()
      val repositoryRelative = Paths.get("shared", "battle", "maps").toAbsolutePath.normalize()
      if Files.isDirectory(backendRelative) then backendRelative
      else repositoryRelative
    }

  private def upsertMapFile(connection: Connection, path: Path, updatedAt: Instant): IO[Unit] =
    IO.blocking(Files.readString(path)).flatMap { rawJson =>
      decode[DefaultMapMetadataJson](rawJson) match {
        case Left(error) =>
          IO.raiseError(IllegalStateException(s"Invalid default battle map JSON ${path.getFileName}: ${error.getMessage}"))
        case Right(metadata) =>
          BattleWorldRuleTable.upsertMap(
            connection = connection,
            mapId = BattleMapId(metadata.mapId),
            active = true,
            themeId = metadata.themeId,
            worldSize = BattleVector2(metadata.worldSize.x, metadata.worldSize.y),
            mapSpecJson = BattleWorldMapSpecJson(rawJson),
            updatedAt = updatedAt
          )
      }
    }

  private final case class DefaultMapMetadataJson(
    mapId: String,
    themeId: String,
    worldSize: DefaultMapVectorJson
  )

  private object DefaultMapMetadataJson {
    given Decoder[DefaultMapMetadataJson] = deriveDecoder
  }

  private final case class DefaultMapVectorJson(x: Double, y: Double)

  private object DefaultMapVectorJson {
    given Decoder[DefaultMapVectorJson] = deriveDecoder
  }
}
