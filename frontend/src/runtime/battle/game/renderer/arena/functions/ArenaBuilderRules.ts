import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ArenaObstacle, BattleMapThemeId } from "../../../objects/BattleGameConstants";
import type {
  CollisionShape,
  CollisionShapeSpec,
  MapBuildingDefinition,
  MapObstacleDefinition
} from "../../../../microservices/world/services/BattleArenaCatalog";
import type { OccludableTrigger } from "../objects/ArenaBuilderObjects";

export function buildingWallObstacle(
  building: MapBuildingDefinition,
  wall: MapBuildingDefinition["walls"][number]
): ArenaObstacle {
  const shape = shapeFromSpec(wall.collision);
  const position = wall.collision.position ?? building.position;
  return {
    obstacleId: `${building.buildingId}-${wall.wallId}`,
    kind: "building-wall",
    position: { x: position.x, y: position.y },
    size: collisionBoundsSize(wall.collision),
    shape
  };
}

export function shapeFromSpec(collision: CollisionShapeSpec): CollisionShape {
  if (collision.kind === "circle") {
    return { kind: "circle", radius: collision.radius };
  }

  return { kind: "aabb", size: { x: collision.size.x, y: collision.size.y } };
}

export function collisionBoundsSize(collision: CollisionShapeSpec): Vec2 {
  if (collision.kind === "circle") {
    return { x: collision.radius * 2, y: collision.radius * 2 };
  }

  return { x: collision.size.x, y: collision.size.y };
}

export function triggerFromCollision(fallbackPosition: Vec2, collision: CollisionShapeSpec): OccludableTrigger {
  const position = collision.position ?? fallbackPosition;
  if (collision.kind === "circle") {
    return { kind: "circle", position: { x: position.x, y: position.y }, radius: collision.radius };
  }

  return {
    kind: "aabb",
    position: { x: position.x, y: position.y },
    size: { x: collision.size.x, y: collision.size.y }
  };
}

export function mapDecorativeKind(kind: MapObstacleDefinition["kind"]): ArenaObstacle["kind"] {
  switch (kind) {
    case "rock":
      return "rock";
    case "logs":
      return "logs";
    case "hay":
      return "hay";
    case "stump":
      return "stump";
    case "bush":
    case "leaf_pile":
      return "crate";
  }
}

export function doorTextureForTheme(themeId: BattleMapThemeId): string {
  return themeId === "fall" ? "fall-door" : "shared-door";
}

export function backgroundColorForTheme(themeId: BattleMapThemeId): string {
  switch (themeId) {
    case "fall":
      return "#243526";
    case "winter":
      return "#c7dce5";
    case "normal":
      return "#1f351f";
    case "industrial":
      return "#0d0f0f";
  }
}

export function depthForObstacle(obstacle: ArenaObstacle): number {
  switch (obstacle.kind) {
    case "wall":
    case "building-wall":
      return 54;
    case "tree-trunk":
      return 49;
    case "rock":
    case "logs":
    case "hay":
    case "stump":
      return 47;
    case "crate":
      return 46;
  }
}
