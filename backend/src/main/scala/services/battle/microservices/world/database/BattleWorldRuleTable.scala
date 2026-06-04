package services.battle.microservices.world.database

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp}
import java.time.Instant
import java.util.UUID

import cats.effect.IO

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import services.battle.microservices.world.objects.world.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.abilities.objects.pickup.{PickupId, PickupKind}
import services.battle.objects.core.{BattleMapId, BattleVector2, Radius}
import services.battle.microservices.abilities.objects.pickup.BattlePickupDefinition
import services.battle.microservices.extraction.objects.extraction.{
  BattleExtractionZoneDefinition,
  BattleExtractionZoneId,
  BattleGasDamagePerSecond,
  BattleGasPlanDefinition,
  BattleGasStageDefinition,
  BattleGasStageIndex,
  BattleLootCacheDefinition,
  BattleLootCacheId,
  BattleLootScoreValue
}
import system.database.PostgresSupport

private[services] object BattleWorldRuleTable {
  private val upsertWorldSql: String =
    """INSERT INTO battle_world_rules (
      |  rule_id, active, floor_tile_size, motion_step_size, player_collision_radius,
      |  projectile_birth_clearance, projectile_shooter_advantage_radius, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  floor_tile_size = EXCLUDED.floor_tile_size,
      |  motion_step_size = EXCLUDED.motion_step_size,
      |  player_collision_radius = EXCLUDED.player_collision_radius,
      |  projectile_birth_clearance = EXCLUDED.projectile_birth_clearance,
      |  projectile_shooter_advantage_radius = EXCLUDED.projectile_shooter_advantage_radius,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  private val upsertMovementSql: String =
    """INSERT INTO battle_world_movement_rules (
      |  rule_id, active, walk_speed, sprint_speed, stamina_drain_per_second,
      |  stamina_recover_per_second, slow_field_movement_factor,
      |  slow_field_projectile_factor, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  walk_speed = EXCLUDED.walk_speed,
      |  sprint_speed = EXCLUDED.sprint_speed,
      |  stamina_drain_per_second = EXCLUDED.stamina_drain_per_second,
      |  stamina_recover_per_second = EXCLUDED.stamina_recover_per_second,
      |  slow_field_movement_factor = EXCLUDED.slow_field_movement_factor,
      |  slow_field_projectile_factor = EXCLUDED.slow_field_projectile_factor,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  private val upsertMapSql: String =
    """INSERT INTO battle_world_map_rules (
      |  map_id, active, theme_id, world_size_x, world_size_y, map_spec_json, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (map_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  theme_id = EXCLUDED.theme_id,
      |  world_size_x = EXCLUDED.world_size_x,
      |  world_size_y = EXCLUDED.world_size_y,
      |  map_spec_json = EXCLUDED.map_spec_json,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  def upsertWorld(
    connection: Connection,
    ruleId: UUID,
    active: Boolean,
    config: BattleWorldRuleConfig,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertWorldSql) { statement =>
        bindWorld(statement, ruleId, active, config, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def upsertMovement(
    connection: Connection,
    ruleId: UUID,
    active: Boolean,
    config: BattleMovementRuleConfig,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertMovementSql) { statement =>
        bindMovement(statement, ruleId, active, config, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def upsertMap(
    connection: Connection,
    mapId: BattleMapId,
    active: Boolean,
    themeId: String,
    worldSize: BattleVector2,
    mapSpecJson: BattleWorldMapSpecJson,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertMapSql) { statement =>
        bindMap(statement, mapId, active, themeId, worldSize, mapSpecJson, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def load(connection: Connection): IO[BattleWorldRuleSet] =
    for {
      world <- loadActiveWorldRules(connection)
      movement <- loadActiveMovementRules(connection)
      maps <- loadActiveMaps(connection)
      _ <- IO.raiseWhen(maps.isEmpty)(IllegalStateException("PostgreSQL table battle_world_map_rules has no active rows."))
    } yield BattleWorldRuleSet(
      world = world,
      movement = movement,
      mapsById = maps.map(map => map.mapId -> map).toMap
    )

  private def loadActiveWorldRules(connection: Connection): IO[BattleWorldRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT floor_tile_size, motion_step_size, player_collision_radius,
          |  projectile_birth_clearance, projectile_shooter_advantage_radius
          |FROM battle_world_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then
            BattleWorldRuleConfig(
              floorTileSize = BattleWorldTileSize(resultSet.getInt("floor_tile_size")),
              motionStepSize = BattleWorldMotionStepSize(resultSet.getDouble("motion_step_size")),
              playerCollisionRadius = Radius(resultSet.getDouble("player_collision_radius")),
              projectileBirthClearance = Radius(resultSet.getDouble("projectile_birth_clearance")),
              projectileShooterAdvantageRadius = Radius(resultSet.getDouble("projectile_shooter_advantage_radius"))
            )
          else throw IllegalStateException("Missing active battle_world_rules row.")
        }
      }
    }

  private def loadActiveMovementRules(connection: Connection): IO[BattleMovementRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT walk_speed, sprint_speed, stamina_drain_per_second,
          |  stamina_recover_per_second, slow_field_movement_factor,
          |  slow_field_projectile_factor
          |FROM battle_world_movement_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then
            BattleMovementRuleConfig(
              walkSpeed = BattleMovementSpeed(resultSet.getDouble("walk_speed")),
              sprintSpeed = BattleMovementSpeed(resultSet.getDouble("sprint_speed")),
              staminaDrainPerSecond = BattleStaminaRatePerSecond(resultSet.getDouble("stamina_drain_per_second")),
              staminaRecoverPerSecond = BattleStaminaRatePerSecond(resultSet.getDouble("stamina_recover_per_second")),
              slowFieldMovementFactor = BattleSlowFactor(resultSet.getDouble("slow_field_movement_factor")),
              slowFieldProjectileFactor = BattleSlowFactor(resultSet.getDouble("slow_field_projectile_factor"))
            )
          else throw IllegalStateException("Missing active battle_world_movement_rules row.")
        }
      }
    }

  private def loadActiveMaps(connection: Connection): IO[Vector[BattleLoadedMapSpec]] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT map_id, theme_id, world_size_x, world_size_y, map_spec_json
          |FROM battle_world_map_rules
          |WHERE active = TRUE
          |ORDER BY map_id ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val maps = Vector.newBuilder[BattleLoadedMapSpec]
          while resultSet.next() do maps += readMap(resultSet)
          maps.result()
        }
      }
    }

  private def readMap(resultSet: ResultSet): BattleLoadedMapSpec = {
    val mapId = BattleMapId(resultSet.getString("map_id"))
    val mapSpecJson = BattleWorldMapSpecJson(resultSet.getString("map_spec_json"))
    val payload = decode[BattleMapPayloadJson](mapSpecJson.value)
      .fold(error => throw IllegalStateException(s"Invalid battle_world_map_rules.map_spec_json for ${mapId.value}: ${error.getMessage}"), identity)

    BattleLoadedMapSpec(
      mapId = mapId,
      themeId = resultSet.getString("theme_id"),
      worldSize = BattleVector2(resultSet.getDouble("world_size_x"), resultSet.getDouble("world_size_y")),
      spawnPoints = payload.heroDefinitions.map(_.position.toDomain),
      collisionObstacles =
        payload.obstacles.flatMap(obstacleFromMapObject) ++
          payload.trees.map(obstacleFromTree) ++
          payload.buildings.flatMap(obstaclesFromBuilding),
      pickupDefinitions = payload.itemPickups.map(itemPickupDefinition) ++ payload.weaponPickups.map(weaponPickupDefinition),
      extractionZones = payload.extractionZones.getOrElse(Vector.empty).map(extractionZoneDefinition),
      lootCaches = payload.lootCaches.getOrElse(Vector.empty).map(lootCacheDefinition),
      gasPlan = payload.gasPlan.map(gasPlanDefinition)
    )
  }

  private def obstacleFromMapObject(obstacle: BattleMapObstacleJson): Option[ArenaObstacle] =
    obstacle.collision.map { collision =>
      ArenaObstacle(
        obstacleId = obstacle.obstacleId,
        kind = required(ArenaObstacleKind.fromWire(obstacle.kind), s"obstacle kind ${obstacle.kind}"),
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

  private def itemPickupDefinition(pickup: BattleMapItemPickupJson): BattlePickupDefinition = {
    val pickupKind = required(PickupKind.fromWire(pickup.kind), s"pickup kind ${pickup.kind}")
    if pickupKind != PickupKind.Medkit then throw IllegalStateException(s"Item pickup must be Medkit: ${pickup.pickupId}")
    BattlePickupDefinition(
      pickupId = PickupId(pickup.pickupId),
      pickupKind = pickupKind,
      weaponKind = None,
      position = pickup.position.toDomain
    )
  }

  private def weaponPickupDefinition(pickup: BattleMapWeaponPickupJson): BattlePickupDefinition =
    BattlePickupDefinition(
      pickupId = PickupId(pickup.pickupId),
      pickupKind = PickupKind.Weapon,
      weaponKind = Some(required(WeaponKind.fromWire(pickup.weaponKind), s"weapon kind ${pickup.weaponKind}")),
      position = pickup.position.toDomain
    )

  private def extractionZoneDefinition(zone: BattleMapExtractionZoneJson): BattleExtractionZoneDefinition =
    BattleExtractionZoneDefinition(
      zoneId = BattleExtractionZoneId(zone.zoneId),
      position = zone.position.toDomain,
      radius = Radius(zone.radius),
      availableFrom = services.battle.objects.core.ElapsedMillis(math.max(0L, zone.availableFromMs)),
      channelDuration = services.battle.objects.core.DurationMillis(math.max(1L, zone.channelDurationMs))
    )

  private def lootCacheDefinition(cache: BattleMapLootCacheJson): BattleLootCacheDefinition =
    BattleLootCacheDefinition(
      cacheId = BattleLootCacheId(cache.cacheId),
      position = cache.position.toDomain,
      radius = Radius(cache.radius),
      searchDuration = services.battle.objects.core.DurationMillis(math.max(1L, cache.searchDurationMs)),
      scoreValue = BattleLootScoreValue(math.max(0, cache.scoreValue))
    )

  private def gasPlanDefinition(plan: BattleMapGasPlanJson): BattleGasPlanDefinition =
    BattleGasPlanDefinition(
      center = plan.center.toDomain,
      stages = plan.stages.zipWithIndex.map { case (stage, index) =>
        BattleGasStageDefinition(
          stageIndex = BattleGasStageIndex(index),
          startsAt = services.battle.objects.core.ElapsedMillis(math.max(0L, stage.startsAtMs)),
          duration = services.battle.objects.core.DurationMillis(math.max(1L, stage.durationMs)),
          fromRadius = Radius(math.max(0.0, stage.fromRadius)),
          toRadius = Radius(math.max(0.0, stage.toRadius)),
          damagePerSecond = BattleGasDamagePerSecond(math.max(0.0, stage.damagePerSecond))
        )
      }
    )

  private def required[A](value: Option[A], label: String): A =
    value.getOrElse(throw IllegalStateException(s"Invalid battle world map payload value: $label"))

  private def bindWorld(
    statement: PreparedStatement,
    ruleId: UUID,
    active: Boolean,
    config: BattleWorldRuleConfig,
    updatedAt: Instant
  ): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, active)
    statement.setInt(3, config.floorTileSize.value)
    statement.setDouble(4, config.motionStepSize.value)
    statement.setDouble(5, config.playerCollisionRadius.value)
    statement.setDouble(6, config.projectileBirthClearance.value)
    statement.setDouble(7, config.projectileShooterAdvantageRadius.value)
    statement.setTimestamp(8, Timestamp.from(updatedAt))
  }

  private def bindMovement(
    statement: PreparedStatement,
    ruleId: UUID,
    active: Boolean,
    config: BattleMovementRuleConfig,
    updatedAt: Instant
  ): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, active)
    statement.setDouble(3, config.walkSpeed.value)
    statement.setDouble(4, config.sprintSpeed.value)
    statement.setDouble(5, config.staminaDrainPerSecond.value)
    statement.setDouble(6, config.staminaRecoverPerSecond.value)
    statement.setDouble(7, config.slowFieldMovementFactor.value)
    statement.setDouble(8, config.slowFieldProjectileFactor.value)
    statement.setTimestamp(9, Timestamp.from(updatedAt))
  }

  private def bindMap(
    statement: PreparedStatement,
    mapId: BattleMapId,
    active: Boolean,
    themeId: String,
    worldSize: BattleVector2,
    mapSpecJson: BattleWorldMapSpecJson,
    updatedAt: Instant
  ): Unit = {
    statement.setString(1, mapId.value)
    statement.setBoolean(2, active)
    statement.setString(3, themeId)
    statement.setDouble(4, worldSize.x)
    statement.setDouble(5, worldSize.y)
    statement.setString(6, mapSpecJson.value)
    statement.setTimestamp(7, Timestamp.from(updatedAt))
  }
}

private final case class BattleMapPayloadJson(
  heroDefinitions: Vector[BattleMapHeroJson],
  trees: Vector[BattleMapTreeJson],
  obstacles: Vector[BattleMapObstacleJson],
  buildings: Vector[BattleMapBuildingJson],
  weaponPickups: Vector[BattleMapWeaponPickupJson],
  itemPickups: Vector[BattleMapItemPickupJson],
  extractionZones: Option[Vector[BattleMapExtractionZoneJson]],
  lootCaches: Option[Vector[BattleMapLootCacheJson]],
  gasPlan: Option[BattleMapGasPlanJson]
)

private object BattleMapPayloadJson {
  given Decoder[BattleMapPayloadJson] = deriveDecoder
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

private final case class BattleMapExtractionZoneJson(
  zoneId: String,
  position: BattleMapVectorJson,
  radius: Double,
  availableFromMs: Long,
  channelDurationMs: Long
)

private object BattleMapExtractionZoneJson {
  given Decoder[BattleMapExtractionZoneJson] = deriveDecoder
}

private final case class BattleMapLootCacheJson(
  cacheId: String,
  position: BattleMapVectorJson,
  radius: Double,
  searchDurationMs: Long,
  scoreValue: Int
)

private object BattleMapLootCacheJson {
  given Decoder[BattleMapLootCacheJson] = deriveDecoder
}

private final case class BattleMapGasStageJson(
  startsAtMs: Long,
  durationMs: Long,
  fromRadius: Double,
  toRadius: Double,
  damagePerSecond: Double
)

private object BattleMapGasStageJson {
  given Decoder[BattleMapGasStageJson] = deriveDecoder
}

private final case class BattleMapGasPlanJson(
  center: BattleMapVectorJson,
  stages: Vector[BattleMapGasStageJson]
)

private object BattleMapGasPlanJson {
  given Decoder[BattleMapGasPlanJson] = deriveDecoder
}
