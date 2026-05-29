package services.battle.microservices.world.objects.world

import services.battle.objects.core.{BattleMapId, BattleVector2, Radius}
import services.battle.microservices.abilities.objects.pickup.BattlePickupDefinition

private[services] enum ArenaObstacleKind {
  case Wall
  case Crate
  case TreeTrunk
  case BuildingWall
  case Rock
  case Logs
  case Hay
  case Stump
}

private[services] object ArenaObstacleKind {
  def fromWire(value: String): Option[ArenaObstacleKind] =
    value match {
      case "wall"          => Some(ArenaObstacleKind.Wall)
      case "crate"         => Some(ArenaObstacleKind.Crate)
      case "tree_trunk"    => Some(ArenaObstacleKind.TreeTrunk)
      case "building_wall" => Some(ArenaObstacleKind.BuildingWall)
      case "rock"          => Some(ArenaObstacleKind.Rock)
      case "logs"          => Some(ArenaObstacleKind.Logs)
      case "hay"           => Some(ArenaObstacleKind.Hay)
      case "stump"         => Some(ArenaObstacleKind.Stump)
      case _               => None
    }
}

private[services] enum ArenaObstacleShape {
  case Aabb(size: BattleVector2)
  case Circle(radius: Double)

  def boundsSize: BattleVector2 =
    this match {
      case ArenaObstacleShape.Aabb(size)     => size
      case ArenaObstacleShape.Circle(radius) => BattleVector2(radius * 2.0, radius * 2.0)
    }
}

private[services] final case class ArenaObstacle(
  obstacleId: String,
  kind: ArenaObstacleKind,
  position: BattleVector2,
  shape: ArenaObstacleShape
) {
  def size: BattleVector2 =
    shape.boundsSize
}

private[services] object ArenaObstacle {
  def aabb(
    obstacleId: String,
    kind: ArenaObstacleKind,
    position: BattleVector2,
    size: BattleVector2
  ): ArenaObstacle =
    ArenaObstacle(
      obstacleId = obstacleId,
      kind = kind,
      position = position,
      shape = ArenaObstacleShape.Aabb(size)
    )
}

private[services] final case class BattleLoadedMapSpec(
  mapId: BattleMapId,
  themeId: String,
  worldSize: BattleVector2,
  spawnPoints: Vector[BattleVector2],
  collisionObstacles: Vector[ArenaObstacle],
  pickupDefinitions: Vector[BattlePickupDefinition]
)

private[services] final case class BattleWorldMapSpecJson(value: String) extends AnyVal

private[services] final case class BattleWorldTileSize(value: Int) extends AnyVal
private[services] final case class BattleWorldMotionStepSize(value: Double) extends AnyVal

private[services] final case class BattleWorldRuleConfig(
  floorTileSize: BattleWorldTileSize,
  motionStepSize: BattleWorldMotionStepSize,
  playerCollisionRadius: Radius,
  projectileBirthClearance: Radius,
  projectileShooterAdvantageRadius: Radius
)

private[services] final case class BattleMovementSpeed(value: Double) extends AnyVal
private[services] final case class BattleStaminaRatePerSecond(value: Double) extends AnyVal
private[services] final case class BattleSlowFactor(value: Double) extends AnyVal

private[services] final case class BattleMovementRuleConfig(
  walkSpeed: BattleMovementSpeed,
  sprintSpeed: BattleMovementSpeed,
  staminaDrainPerSecond: BattleStaminaRatePerSecond,
  staminaRecoverPerSecond: BattleStaminaRatePerSecond,
  slowFieldMovementFactor: BattleSlowFactor,
  slowFieldProjectileFactor: BattleSlowFactor
)

private[services] final case class BattleWorldRuleSet(
  world: BattleWorldRuleConfig,
  movement: BattleMovementRuleConfig,
  mapsById: Map[BattleMapId, BattleLoadedMapSpec]
)
