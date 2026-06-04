import type { ArenaObstacleShape } from "../../../../../objects/battle/microservices/world/objects/world/BattleWorldRuleDefinitions";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export type BattleArenaObstacleShape = ArenaObstacleShape;

export interface BattleArenaObstacleBounds {
  position: Vec2;
  size: Vec2;
  shape?: BattleArenaObstacleShape;
}

export function isInWorld(position: Vec2, radius: number, worldSize: Vec2): boolean {
  return (
    position.x >= radius &&
    position.x <= worldSize.x - radius &&
    position.y >= radius &&
    position.y <= worldSize.y - radius
  );
}

export function canPlayerOccupy(
  position: Vec2,
  radius: number,
  worldSize: Vec2,
  obstacleBounds: readonly BattleArenaObstacleBounds[]
): boolean {
  return isInWorld(position, radius, worldSize) && !collidesWithArenaObstacles(position, radius, obstacleBounds);
}

export function collidesWithArenaObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly BattleArenaObstacleBounds[]
): boolean {
  return obstacleBounds.some((obstacle) => intersectsObstacle(position, radius, obstacle));
}

export function intersectsObstacle(position: Vec2, radius: number, obstacle: BattleArenaObstacleBounds): boolean {
  if (obstacle.shape?.kind === "circle") {
    return Math.hypot(position.x - obstacle.position.x, position.y - obstacle.position.y) < radius + obstacle.shape.radius;
  }

  const size = obstacle.shape?.kind === "aabb" ? obstacle.shape.size : obstacle.size;
  const dx = Math.abs(position.x - obstacle.position.x);
  const dy = Math.abs(position.y - obstacle.position.y);
  return dx < radius + size.x / 2 && dy < radius + size.y / 2;
}
