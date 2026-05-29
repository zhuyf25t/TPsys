package services.battle

import java.nio.file.{Files, Path, Paths}

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode

import services.battle.microservices.abilities.database.{BattlePickupRuleBook, BattleSkillRuleBook}
import services.battle.microservices.abilities.objects.abilities.*
import services.battle.microservices.actors.database.BattleBotRuleBook
import services.battle.microservices.actors.objects.actors.*
import services.battle.microservices.actors.objects.player.{HitPoints, Stamina}
import services.battle.microservices.combat.database.BattleCombatRuleBook
import services.battle.microservices.combat.objects.combat.*
import services.battle.microservices.runtime.database.BattleRuntimeRuleBook
import services.battle.microservices.runtime.objects.runtime.*
import services.battle.microservices.world.database.BattleWorldRuleBook
import services.battle.microservices.world.objects.world.*
import services.battle.microservices.combat.objects.projectile.ProjectileKind
import services.battle.microservices.combat.objects.weapon.{BattleWeaponHeat, BattleWeaponHeatRatePerSecond, WeaponKind}
import services.battle.microservices.abilities.objects.pickup.{PickupId, PickupKind}
import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.objects.core.*
import services.battle.microservices.abilities.objects.pickup.BattlePickupDefinition

object BattleDynamicRuleTestDefaults:
  def install(): Unit =
    BattleWorldRuleBook.replace(worldRules)
    BattleRuntimeRuleBook.replace(runtimeRules)
    BattleCombatRuleBook.replaceAll(weaponRules)
    BattleSkillRuleBook.replace(skillRules)
    BattlePickupRuleBook.replace(pickupRules)
    BattleBotRuleBook.replace(botRules)

  private def worldRules: BattleWorldRuleSet =
    BattleWorldRuleSet(
      world = BattleWorldRuleConfig(
        floorTileSize = BattleWorldTileSize(64),
        motionStepSize = BattleWorldMotionStepSize(16.0),
        playerCollisionRadius = Radius(18.0),
        projectileBirthClearance = Radius(4.0),
        projectileShooterAdvantageRadius = Radius(6.0)
      ),
      movement = BattleMovementRuleConfig(
        walkSpeed = BattleMovementSpeed(255.0),
        sprintSpeed = BattleMovementSpeed(255.0 * 1.75),
        staminaDrainPerSecond = BattleStaminaRatePerSecond(38.0),
        staminaRecoverPerSecond = BattleStaminaRatePerSecond(24.0),
        slowFieldMovementFactor = BattleSlowFactor(0.5),
        slowFieldProjectileFactor = BattleSlowFactor(0.5)
      ),
      mapsById = loadMaps.map(map => map.mapId -> map).toMap
    )

  private val runtimeRules: BattleRuntimeRuleSet =
    BattleRuntimeRuleSet(
      runtime = BattleRuntimeRuleConfig(
        defaultBattleDuration = DurationMillis(5L * 60L * 1000L),
        tickStep = DurationMillis(33L)
      ),
      history = BattleHistoryRuleConfig(
        retainedProjectileTerminalCount = BattleHistoryCount(64),
        retainedBattleEventCount = BattleHistoryCount(12),
        replayFrameSampleInterval = DurationMillis(1000L),
        retainedReplayFrameCount = BattleHistoryCount(32)
      ),
      sessionPlayer = BattleSessionPlayerRuleConfig(
        initialHp = HitPoints(100),
        maxHp = HitPoints(100),
        initialStamina = Stamina(100.0),
        maxStamina = Stamina(100.0),
        defaultWeaponKind = WeaponKind.Pistol
      )
    )

  private val weaponRules: Vector[BattleWeaponRuleDefinition] =
    Vector(
      weaponRule(
        weaponKind = WeaponKind.Pistol,
        projectileKind = ProjectileKind.PistolBullet,
        cooldownMs = 260,
        reloadMs = 1000,
        speed = 1400.0,
        damage = 12,
        lifetimeMs = 30000L,
        projectileRadius = 8.0,
        splashRadius = 0.0,
        projectileCount = 1,
        spread = 0.0,
        magazineSize = 12,
        reserveAmmo = Some(48),
        pickupAmmo = 24,
        recoilStrength = 20.0
      ),
      weaponRule(
        weaponKind = WeaponKind.RocketLauncher,
        projectileKind = ProjectileKind.Rocket,
        cooldownMs = 160,
        reloadMs = 2500,
        speed = 340.0,
        damage = 60,
        lifetimeMs = 30000L,
        projectileRadius = 14.0,
        splashRadius = 132.0,
        projectileCount = 1,
        spread = 0.0,
        magazineSize = 1,
        reserveAmmo = Some(3),
        pickupAmmo = 1,
        recoilStrength = 120.0
      ),
      weaponRule(
        weaponKind = WeaponKind.Gatling,
        projectileKind = ProjectileKind.GatlingBullet,
        cooldownMs = 72,
        reloadMs = 0,
        speed = 980.0,
        damage = 5,
        lifetimeMs = 30000L,
        projectileRadius = 7.0,
        splashRadius = 0.0,
        projectileCount = 1,
        spread = 0.06,
        magazineSize = 0,
        reserveAmmo = Some(0),
        pickupAmmo = 0,
        recoilStrength = 8.0,
        heat = Some(
          BattleWeaponHeatDefinition(
            maxHeat = BattleWeaponHeat(100),
            heatPerShot = BattleWeaponHeat(8),
            coolRatePerSecond = BattleWeaponHeatRatePerSecond(32),
            overheatLockMs = CooldownMillis(1400)
          )
        )
      ),
      weaponRule(
        weaponKind = WeaponKind.Shotgun,
        projectileKind = ProjectileKind.ShotgunPellet,
        cooldownMs = 760,
        reloadMs = 1200,
        speed = 720.0,
        damage = 8,
        lifetimeMs = 30000L,
        projectileRadius = 7.0,
        splashRadius = 0.0,
        projectileCount = 5,
        spread = 0.42,
        magazineSize = 6,
        reserveAmmo = Some(18),
        pickupAmmo = 6,
        recoilStrength = 80.0
      )
    )

  private val skillRules: BattleSkillRuleSet =
    BattleSkillRuleSet(
      blink = BlinkConfig(
        range = SkillDistance(250.0),
        runtime = BattleSkillRuntime(CooldownMillis(2200), DurationMillis(240L))
      ),
      dash = DashConfig(
        distance = SkillDistance(180.0),
        runtime = BattleSkillRuntime(CooldownMillis(5000), DurationMillis(180L))
      ),
      freeze = FreezeConfig(
        radius = Radius(150.0),
        castRange = SkillDistance(520.0),
        runtime = BattleSkillRuntime(CooldownMillis(12000), DurationMillis(10000L))
      )
    )

  private val pickupRules: BattlePickupRuleConfig =
    BattlePickupRuleConfig(
      contactRadius = Radius(40.0),
      respawnDuration = DurationMillis(10000L),
      medkitHeal = HitPoints(25)
    )

  private val botRules: BattleBotRuleConfig =
    BattleBotRuleConfig(
      moveSpeed = BattleBotMoveSpeed(108.0),
      preferredRange = Radius(260.0),
      preferredRangeAdvanceMargin = Radius(80.0),
      preferredRangeRetreatMargin = Radius(90.0),
      botFireRange = Radius(520.0),
      humanFireRange = Radius(360.0),
      openingFireDelay = DurationMillis(5000L),
      firePulseInterval = DurationMillis(1000L),
      firePulseWindow = DurationMillis(1000L),
      movementProbeDistance = Radius(96.0),
      coverProbeDistance = Radius(220.0),
      pickupSeekRange = Radius(380.0),
      aimLeadDistance = Radius(0.16),
      aimErrorRadius = Radius(0.02),
      lowHealthRatio = 0.38,
      pickupHealthRatio = 0.52,
      tacticalReloadRatio = 0.28
    )

  private def weaponRule(
    weaponKind: WeaponKind,
    projectileKind: ProjectileKind,
    cooldownMs: Int,
    reloadMs: Int,
    speed: Double,
    damage: Int,
    lifetimeMs: Long,
    projectileRadius: Double,
    splashRadius: Double,
    projectileCount: Int,
    spread: Double,
    magazineSize: Int,
    reserveAmmo: Option[Int],
    pickupAmmo: Int,
    recoilStrength: Double,
    heat: Option[BattleWeaponHeatDefinition] = None
  ): BattleWeaponRuleDefinition =
    BattleWeaponRuleDefinition(
      inventory = BattleWeaponInventoryDefinition(
        weaponKind = weaponKind,
        magazineSize = magazineSize,
        reserveAmmo = reserveAmmo,
        pickupAmmo = pickupAmmo,
        reloadMs = reloadMs,
        firingResource = heat.fold(BattleWeaponFiringResource.Magazine)(_ => BattleWeaponFiringResource.Heat)
      ),
      fire = BattleWeaponFireDefinition(
        weaponKind = weaponKind,
        cooldownMs = CooldownMillis(cooldownMs),
        projectile = BattleWeaponProjectileDefinition(
          projectileKind = projectileKind,
          speed = BattleWeaponProjectileSpeed(speed),
          damage = Damage(damage),
          radius = Radius(projectileRadius),
          lifetime = DurationMillis(lifetimeMs),
          splashRadius = Radius(splashRadius),
          projectileCount = BattleWeaponProjectileCount(projectileCount),
          spread = FacingRadians(spread)
        ),
        recoilStrength = BattleWeaponRecoilStrength(recoilStrength),
        heat = heat
      )
    )

  private def loadMaps: Vector[BattleLoadedMapSpec] =
    mapFiles.map(loadMap)

  private def mapFiles: Vector[Path] = {
    val candidates = Vector(
      Paths.get("..", "shared", "battle", "maps"),
      Paths.get("shared", "battle", "maps")
    ).map(_.toAbsolutePath.normalize)
    val root = candidates.find(Files.isDirectory(_)).getOrElse {
      throw IllegalStateException("Missing shared battle maps directory.")
    }

    Vector(
      "default-industrial-arena.json",
      "fall-hunt-v1.json",
      "winter-hunt-v1.json",
      "normal-hunt-v1.json"
    ).map(root.resolve)
  }

  private def loadMap(path: Path): BattleLoadedMapSpec = {
    val payload = decode[BattleMapPayloadJson](Files.readString(path))
      .fold(error => throw IllegalStateException(s"Invalid battle map JSON ${path.getFileName}: ${error.getMessage}"), identity)
    BattleLoadedMapSpec(
      mapId = BattleMapId(payload.mapId),
      themeId = payload.themeId,
      worldSize = payload.worldSize.toDomain,
      spawnPoints = spawnPointsFor(payload),
      collisionObstacles =
        payload.obstacles.flatMap(obstacleFromMapObject) ++
          payload.trees.map(obstacleFromTree) ++
          payload.buildings.flatMap(obstaclesFromBuilding),
      pickupDefinitions = payload.itemPickups.map(itemPickupDefinition) ++ payload.weaponPickups.map(weaponPickupDefinition)
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

  private def required[A](value: Option[A], label: String): A =
    value.getOrElse(throw IllegalStateException(s"Invalid battle map payload value: $label"))

  private def spawnPointsFor(payload: BattleMapPayloadJson): Vector[BattleVector2] = {
    val explicit = payload.heroDefinitions.map(_.position.toDomain)
    if explicit.length >= 12 then explicit
    else explicit ++ fallbackSpawnPoints(payload.worldSize.toDomain).drop(explicit.length).take(12 - explicit.length)
  }

  private def fallbackSpawnPoints(worldSize: BattleVector2): Vector[BattleVector2] =
    val margin = 220.0
    val center = BattleVector2(worldSize.x / 2.0, worldSize.y / 2.0)
    Vector(
      BattleVector2(margin, margin),
      BattleVector2(worldSize.x - margin, margin),
      BattleVector2(margin, worldSize.y - margin),
      BattleVector2(worldSize.x - margin, worldSize.y - margin),
      BattleVector2(center.x, margin),
      BattleVector2(center.x, worldSize.y - margin),
      BattleVector2(700.0, center.y),
      BattleVector2(1120.0, center.y),
      BattleVector2(1000.0, center.y),
      BattleVector2(center.x - margin, center.y),
      BattleVector2(center.x + margin, center.y),
      BattleVector2(center.x, center.y + margin)
    )

private final case class BattleMapPayloadJson(
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

private object BattleMapPayloadJson:
  given Decoder[BattleMapPayloadJson] = deriveDecoder

private final case class BattleMapVectorJson(x: Double, y: Double):
  def toDomain: BattleVector2 = BattleVector2(x, y)

private object BattleMapVectorJson:
  given Decoder[BattleMapVectorJson] = deriveDecoder

private final case class BattleMapHeroJson(position: BattleMapVectorJson)

private object BattleMapHeroJson:
  given Decoder[BattleMapHeroJson] = deriveDecoder

private final case class BattleMapCollisionJson(
  kind: String,
  position: Option[BattleMapVectorJson],
  size: Option[BattleMapVectorJson],
  radius: Option[Double]
):
  def positionOr(fallback: BattleMapVectorJson): BattleVector2 =
    position.getOrElse(fallback).toDomain

  def toShape: ArenaObstacleShape =
    kind match
      case "aabb" =>
        ArenaObstacleShape.Aabb(size.getOrElse(throw IllegalArgumentException("AABB collision missing size")).toDomain)
      case "circle" =>
        ArenaObstacleShape.Circle(radius.getOrElse(throw IllegalArgumentException("Circle collision missing radius")))
      case other =>
        throw IllegalArgumentException(s"Unsupported collision shape in battle map spec: $other")

private object BattleMapCollisionJson:
  given Decoder[BattleMapCollisionJson] = deriveDecoder

private final case class BattleMapTreeJson(
  treeId: String,
  position: BattleMapVectorJson,
  trunkCollision: BattleMapCollisionJson
)

private object BattleMapTreeJson:
  given Decoder[BattleMapTreeJson] = deriveDecoder

private final case class BattleMapObstacleJson(
  obstacleId: String,
  kind: String,
  position: BattleMapVectorJson,
  collision: Option[BattleMapCollisionJson]
)

private object BattleMapObstacleJson:
  given Decoder[BattleMapObstacleJson] = deriveDecoder

private final case class BattleMapBuildingWallJson(
  wallId: String,
  collision: BattleMapCollisionJson
)

private object BattleMapBuildingWallJson:
  given Decoder[BattleMapBuildingWallJson] = deriveDecoder

private final case class BattleMapBuildingJson(
  buildingId: String,
  position: BattleMapVectorJson,
  walls: Vector[BattleMapBuildingWallJson]
)

private object BattleMapBuildingJson:
  given Decoder[BattleMapBuildingJson] = deriveDecoder

private final case class BattleMapWeaponPickupJson(
  pickupId: String,
  weaponKind: String,
  position: BattleMapVectorJson
)

private object BattleMapWeaponPickupJson:
  given Decoder[BattleMapWeaponPickupJson] = deriveDecoder

private final case class BattleMapItemPickupJson(
  pickupId: String,
  kind: String,
  position: BattleMapVectorJson
)

private object BattleMapItemPickupJson:
  given Decoder[BattleMapItemPickupJson] = deriveDecoder
