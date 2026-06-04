import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  canPlayerOccupy,
  collidesWithArenaObstacles,
  intersectsObstacle,
  isInWorld,
  type BattleArenaObstacleBounds,
  type BattleArenaObstacleShape
} from "./BattleArenaCollision";

export type MotionObstacleBounds = BattleArenaObstacleBounds;
export type MotionObstacleShape = BattleArenaObstacleShape;

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

interface SteppedMotionResult extends MotionControllerTargetResult {
  hitBlocker: boolean;
}

export function normalizeMovement(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}

export function normalizeVector(vector: Vec2): Vec2 {
  return normalizeMovement(vector);
}

export function findMotionDestination(input: MotionDestinationInput): MotionControllerTargetResult {
  const normalized = normalizeMovement(input.direction);
  const distance = Math.max(0, input.distance);
  const fullMotion = tryResolveSteppedMotion(
    input.position,
    normalized,
    distance,
    input.radius,
    input.worldSize,
    input.obstacleBounds
  );

  if (!fullMotion.hitBlocker) {
    return toMotionResult(fullMotion);
  }

  const xDistance = Math.abs(normalized.x * distance);
  const yDistance = Math.abs(normalized.y * distance);
  const xMotion =
    xDistance > 0
      ? tryResolveSteppedMotion(
          input.position,
          { x: Math.sign(normalized.x), y: 0 },
          xDistance,
          input.radius,
          input.worldSize,
          input.obstacleBounds
        )
      : fullMotion;
  const yMotion =
    yDistance > 0
      ? tryResolveSteppedMotion(
          input.position,
          { x: 0, y: Math.sign(normalized.y) },
          yDistance,
          input.radius,
          input.worldSize,
          input.obstacleBounds
        )
      : fullMotion;

  return toMotionResult(resolveBestMotion(input.position, fullMotion, xMotion, yMotion));
}

function tryResolveSteppedMotion(
  position: Vec2,
  direction: Vec2,
  distance: number,
  radius: number,
  worldSize: Vec2,
  obstacleBounds: readonly MotionObstacleBounds[]
): SteppedMotionResult {
  let lastValid = { x: position.x, y: position.y };
  const clampedDistance = Math.max(0, distance);
  const steps = Math.ceil(clampedDistance / 16);
  let hitBlocker = false;

  for (let step = 1; step <= steps; step += 1) {
    const travel = Math.min(clampedDistance, step * 16);
    const candidate = {
      x: position.x + direction.x * travel,
      y: position.y + direction.y * travel
    };

    if (!canPlayerOccupy(candidate, radius, worldSize, obstacleBounds)) {
      hitBlocker = true;
      break;
    }

    lastValid = candidate;
  }

  return {
    destination: lastValid,
    blocked: lastValid.x === position.x && lastValid.y === position.y,
    hitBlocker
  };
}

function toMotionResult(result: SteppedMotionResult): MotionControllerTargetResult {
  return {
    destination: result.destination,
    blocked: result.blocked
  };
}

function motionProgress(destination: Vec2, origin: Vec2): number {
  return Math.hypot(destination.x - origin.x, destination.y - origin.y);
}

function resolveBestMotion(origin: Vec2, ...motions: readonly SteppedMotionResult[]): SteppedMotionResult {
  return motions.reduce((bestMotion, motion) =>
    motionProgress(motion.destination, origin) > motionProgress(bestMotion.destination, origin) ? motion : bestMotion
  );
}

export function isMotionTargetPointValid(input: MotionTargetInput): boolean {
  return canPlayerOccupy(input.target, input.radius, input.worldSize, input.obstacleBounds);
}

export function canOccupy(
  position: Vec2,
  radius: number,
  worldSize: Vec2,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return canPlayerOccupy(position, radius, worldSize, obstacleBounds);
}

export function collidesWithObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return collidesWithArenaObstacles(position, radius, obstacleBounds);
}

export { intersectsObstacle };
export const isInsideWorld = isInWorld;
