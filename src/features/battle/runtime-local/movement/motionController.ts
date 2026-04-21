import type { Vec2 } from "../../../../domain/types";

export interface MotionObstacleBounds {
  position: Vec2;
  size: Vec2;
}

export interface MotionDestinationInput {
  position: Vec2;
  direction: Vec2;
  distance: number;
  radius: number;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}

export interface MotionTargetInput {
  target: Vec2;
  radius: number;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}

export interface MotionControllerTargetResult {
  destination: Vec2;
  blocked: boolean;
}

function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}

export function findMotionDestination(input: MotionDestinationInput): MotionControllerTargetResult {
  const normalized = normalizeVector(input.direction);
  let lastValid = { x: input.position.x, y: input.position.y };
  const steps = Math.ceil(input.distance / 16);

  for (let step = 1; step <= steps; step += 1) {
    const travel = Math.min(input.distance, step * 16);
    const candidate = {
      x: input.position.x + normalized.x * travel,
      y: input.position.y + normalized.y * travel
    };

    if (!canOccupy(candidate, input.radius, input.worldSize, input.obstacleBounds)) {
      break;
    }

    lastValid = candidate;
  }

  return {
    destination: lastValid,
    blocked: lastValid.x === input.position.x && lastValid.y === input.position.y
  };
}

export function isMotionTargetPointValid(input: MotionTargetInput): boolean {
  return canOccupy(input.target, input.radius, input.worldSize, input.obstacleBounds);
}

export function canOccupy(
  position: Vec2,
  radius: number,
  worldSize: Vec2,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return isInsideWorld(position, radius, worldSize) && !collidesWithObstacles(position, radius, obstacleBounds);
}

export function collidesWithObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return obstacleBounds.some((obstacle) => intersectsObstacle(position, radius, obstacle));
}

export function intersectsObstacle(position: Vec2, radius: number, obstacle: MotionObstacleBounds): boolean {
  const dx = Math.abs(position.x - obstacle.position.x);
  const dy = Math.abs(position.y - obstacle.position.y);
  return dx < radius + obstacle.size.x / 2 && dy < radius + obstacle.size.y / 2;
}

export function isInsideWorld(position: Vec2, radius: number, worldSize: Vec2): boolean {
  return (
    position.x >= radius &&
    position.x <= worldSize.x - radius &&
    position.y >= radius &&
    position.y <= worldSize.y - radius
  );
}
