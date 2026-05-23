import type { Vec2 } from "../../../objects/types";

export interface MotionObstacleBounds {
  position: Vec2;
  size: Vec2;
  shape?: MotionObstacleShape;
}

export type MotionObstacleShape =
  | { kind: "aabb"; size: Vec2 }
  | { kind: "circle"; radius: number };

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

/** 中文名：查找运动destination（findMotionDestination）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function findMotionDestination(input: MotionDestinationInput): MotionControllerTargetResult {
  const normalized = normalizeVector(input.direction);
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

    if (!canOccupy(candidate, radius, worldSize, obstacleBounds)) {
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
    motionProgress(motion.destination, origin) > motionProgress(bestMotion.destination, origin)
      ? motion
      : bestMotion
  );
}

/** 中文名：判断是否运动目标pointvalid（isMotionTargetPointValid）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isMotionTargetPointValid(input: MotionTargetInput): boolean {
  return canOccupy(input.target, input.radius, input.worldSize, input.obstacleBounds);
}

/** 中文名：判断可否occupy（canOccupy）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function canOccupy(
  position: Vec2,
  radius: number,
  worldSize: Vec2,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return isInsideWorld(position, radius, worldSize) && !collidesWithObstacles(position, radius, obstacleBounds);
}

/** 中文名：collideswithobstacles（collidesWithObstacles）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function collidesWithObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly MotionObstacleBounds[]
): boolean {
  return obstacleBounds.some((obstacle) => intersectsObstacle(position, radius, obstacle));
}

/** 中文名：intersectsobstacle（intersectsObstacle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function intersectsObstacle(position: Vec2, radius: number, obstacle: MotionObstacleBounds): boolean {
  if (obstacle.shape?.kind === "circle") {
    return Math.hypot(position.x - obstacle.position.x, position.y - obstacle.position.y) < radius + obstacle.shape.radius;
  }

  const size = obstacle.shape?.kind === "aabb" ? obstacle.shape.size : obstacle.size;
  const dx = Math.abs(position.x - obstacle.position.x);
  const dy = Math.abs(position.y - obstacle.position.y);
  return dx < radius + size.x / 2 && dy < radius + size.y / 2;
}

/** 中文名：判断是否inside世界（isInsideWorld）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isInsideWorld(position: Vec2, radius: number, worldSize: Vec2): boolean {
  return (
    position.x >= radius &&
    position.x <= worldSize.x - radius &&
    position.y >= radius &&
    position.y <= worldSize.y - radius
  );
}
