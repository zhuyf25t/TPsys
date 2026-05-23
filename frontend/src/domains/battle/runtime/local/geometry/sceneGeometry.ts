import type { Vec2 } from "../../../objects/types";
import {
  findMotionDestination,
  collidesWithObstacles as motionCollidesWithObstacles,
  intersectsObstacle as motionIntersectsObstacle,
  type MotionObstacleShape
} from "../movement/motionController";

export interface SceneGeometryObstacleBounds {
  position: Vec2;
  size: Vec2;
  shape?: MotionObstacleShape;
}

export interface FindDashDestinationInput {
  position: Vec2;
  direction: Vec2;
  distance: number;
  radius: number;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

/** 中文名：规范化vector（normalizeVector）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}

/** 中文名：查找dashdestination（findDashDestination）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function findDashDestination(input: FindDashDestinationInput): Vec2 {
  return findMotionDestination({
    position: input.position,
    direction: input.direction,
    distance: input.distance,
    radius: input.radius,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  }).destination;
}

/** 中文名：collideswithobstacles（collidesWithObstacles）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function collidesWithObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): boolean {
  return motionCollidesWithObstacles(position, radius, obstacleBounds);
}

/** 中文名：创建obstaclecollisionadapter（createObstacleCollisionAdapter）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createObstacleCollisionAdapter(
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): (position: Vec2, radius: number) => boolean {
  return (position, radius) => collidesWithObstacles(position, radius, obstacleBounds);
}

/** 中文名：intersectsobstacle（intersectsObstacle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function intersectsObstacle(position: Vec2, radius: number, obstacle: SceneGeometryObstacleBounds): boolean {
  return motionIntersectsObstacle(position, radius, obstacle);
}
