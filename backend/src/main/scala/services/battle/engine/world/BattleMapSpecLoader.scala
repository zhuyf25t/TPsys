package services.battle.engine

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import services.battle.objects.*

private[services] final case class BattleLoadedMapSpec(
  mapId: BattleMapId,
  themeId: String,
  worldSize: BattleVector2,
  spawnPoints: Vector[BattleVector2],
  collisionObstacles: Vector[ArenaObstacle],
  pickupDefinitions: Vector[BattlePickupDefinition]
)

private[services] object BattleMapSpecLoader {
  private val DefaultMapId: BattleMapId = BattleMode.mapId(BattleMode.Default)
  private val MapSpecs: Map[BattleMapId, BattleLoadedMapSpec] =
    Vector("default-industrial-arena.json", "fall-hunt-v1.json")
      .map(fileName =>
        decode[BattleMapJson](readSharedSpec(fileName))
          .fold(
            error => throw IllegalStateException(s"Unable to decode battle map spec $fileName: ${error.getMessage}"),
            toLoadedSpec
          )
      )
      .map(spec => spec.mapId -> spec)
      .toMap

  val Loaded: BattleLoadedMapSpec =
    load(DefaultMapId)

  def load(mapId: BattleMapId): BattleLoadedMapSpec =
    MapSpecs.getOrElse(mapId, MapSpecs(DefaultMapId))

  private def readSharedSpec(fileName: String): String = {
    val candidates = Vector(
      Paths.get("shared", "battle", "maps", fileName),
      Paths.get("..", "shared", "battle", "maps", fileName),
      Paths.get("..", "..", "shared", "battle", "maps", fileName)
    )

    candidates.find(Files.isRegularFile(_)) match {
      case Some(path) => Files.readString(path, StandardCharsets.UTF_8)
      case None =>
        val searched = candidates.map(_.toAbsolutePath.normalize.toString).mkString(", ")
        throw IllegalStateException(s"Unable to locate shared battle map spec $fileName. Searched: $searched")
    }
  }

  private def toLoadedSpec(spec: BattleMapJson): BattleLoadedMapSpec =
    BattleLoadedMapSpec(
      mapId = BattleMapId(spec.mapId),
      themeId = spec.themeId,
      worldSize = spec.worldSize.toDomain,
      spawnPoints = spec.heroDefinitions.map(_.position.toDomain),
      collisionObstacles =
        spec.obstacles.flatMap(obstacleFromMapObject) ++
          spec.trees.map(obstacleFromTree) ++
          spec.buildings.flatMap(obstaclesFromBuilding),
      pickupDefinitions = spec.itemPickups.map(itemPickupDefinition) ++ spec.weaponPickups.map(weaponPickupDefinition)
    )

  private def obstacleFromMapObject(obstacle: BattleMapObstacleJson): Option[ArenaObstacle] =
    obstacle.collision.map { collision =>
      ArenaObstacle(
        obstacleId = obstacle.obstacleId,
        kind = obstacleKind(obstacle.kind),
        position = collision.positionOr(obstacle.position),
        shape = collision.toShape
      )
    }

  private def obstacleFromTree(tree: BattleMapTreeJson): ArenaObstacle =
    ArenaObstacle(
      obstacleId = s"${tree.treeId}-trunk",
      kind = ArenaObstacleKind.TreeTrunk,
      position = tree.trunkCollision.positionOr(tree.position),
      shape = tree.trunkCollision.toShape
    )

  private def obstaclesFromBuilding(building: BattleMapBuildingJson): Vector[ArenaObstacle] =
    building.walls.map { wall =>
      ArenaObstacle(
        obstacleId = s"${building.buildingId}-${wall.wallId}",
        kind = ArenaObstacleKind.BuildingWall,
        position = wall.collision.positionOr(building.position),
        shape = wall.collision.toShape
      )
    }

  private def itemPickupDefinition(pickup: BattleMapItemPickupJson): BattlePickupDefinition =
    BattlePickupDefinition(
      pickupId = PickupId(pickup.pickupId),
      pickupKind = PickupKind.Medkit,
      weaponKind = None,
      position = pickup.position.toDomain
    )

  private def weaponPickupDefinition(pickup: BattleMapWeaponPickupJson): BattlePickupDefinition =
    BattlePickupDefinition(
      pickupId = PickupId(pickup.pickupId),
      pickupKind = PickupKind.Weapon,
      weaponKind = Some(weaponKind(pickup.weaponKind)),
      position = pickup.position.toDomain
    )

  private def obstacleKind(kind: String): ArenaObstacleKind =
    kind match {
      case "rock"  => ArenaObstacleKind.Rock
      case "logs"  => ArenaObstacleKind.Logs
      case "hay"   => ArenaObstacleKind.Hay
      case "stump" => ArenaObstacleKind.Stump
      case _       => ArenaObstacleKind.Crate
    }

  private def weaponKind(value: String): WeaponKind =
    value match {
      case "Pistol"         => WeaponKind.Pistol
      case "RocketLauncher" => WeaponKind.RocketLauncher
      case "Gatling"        => WeaponKind.Gatling
      case "Shotgun"        => WeaponKind.Shotgun
      case other            => throw IllegalArgumentException(s"Unsupported weapon kind in battle map spec: $other")
    }
}

private final case class BattleMapJson(
  mapId: String,
  themeId: String,
  worldSize: BattleMapVectorJson,
  heroDefinitions: Vector[BattleMapHeroJson],
  trees: Vector[BattleMapTreeJson],
  obstacles: Vector[BattleMapObstacleJson],
  buildings: Vector[BattleMapBuildingJson],
  weaponPickups: Vector[BattleMapWeaponPickupJson],
  itemPickups: Vector[BattleMapItemPickupJson]
)

private object BattleMapJson {
  given Decoder[BattleMapJson] = deriveDecoder
}

private final case class BattleMapVectorJson(x: Double, y: Double) {
  def toDomain: BattleVector2 = BattleVector2(x, y)
}

private object BattleMapVectorJson {
  given Decoder[BattleMapVectorJson] = deriveDecoder
}

private final case class BattleMapHeroJson(position: BattleMapVectorJson)

private object BattleMapHeroJson {
  given Decoder[BattleMapHeroJson] = deriveDecoder
}

private final case class BattleMapCollisionJson(
  kind: String,
  position: Option[BattleMapVectorJson],
  size: Option[BattleMapVectorJson],
  radius: Option[Double]
) {
  def positionOr(fallback: BattleMapVectorJson): BattleVector2 =
    position.getOrElse(fallback).toDomain

  def toShape: ArenaObstacleShape =
    kind match {
      case "aabb" =>
        ArenaObstacleShape.Aabb(size.getOrElse(throw IllegalArgumentException("AABB collision missing size")).toDomain)
      case "circle" =>
        ArenaObstacleShape.Circle(radius.getOrElse(throw IllegalArgumentException("Circle collision missing radius")))
      case other =>
        throw IllegalArgumentException(s"Unsupported collision shape in battle map spec: $other")
    }
}

private object BattleMapCollisionJson {
  given Decoder[BattleMapCollisionJson] = deriveDecoder
}

private final case class BattleMapTreeJson(
  treeId: String,
  position: BattleMapVectorJson,
  trunkCollision: BattleMapCollisionJson
)

private object BattleMapTreeJson {
  given Decoder[BattleMapTreeJson] = deriveDecoder
}

private final case class BattleMapObstacleJson(
  obstacleId: String,
  kind: String,
  position: BattleMapVectorJson,
  collision: Option[BattleMapCollisionJson]
)

private object BattleMapObstacleJson {
  given Decoder[BattleMapObstacleJson] = deriveDecoder
}

private final case class BattleMapBuildingWallJson(
  wallId: String,
  collision: BattleMapCollisionJson
)

private object BattleMapBuildingWallJson {
  given Decoder[BattleMapBuildingWallJson] = deriveDecoder
}

private final case class BattleMapBuildingJson(
  buildingId: String,
  position: BattleMapVectorJson,
  walls: Vector[BattleMapBuildingWallJson]
)

private object BattleMapBuildingJson {
  given Decoder[BattleMapBuildingJson] = deriveDecoder
}

private final case class BattleMapWeaponPickupJson(
  pickupId: String,
  weaponKind: String,
  position: BattleMapVectorJson
)

private object BattleMapWeaponPickupJson {
  given Decoder[BattleMapWeaponPickupJson] = deriveDecoder
}

private final case class BattleMapItemPickupJson(
  pickupId: String,
  kind: String,
  position: BattleMapVectorJson
)

private object BattleMapItemPickupJson {
  given Decoder[BattleMapItemPickupJson] = deriveDecoder
}
