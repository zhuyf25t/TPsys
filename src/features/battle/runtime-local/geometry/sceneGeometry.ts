import type { Vec2 } from "../../../../domain/types";
import { findMotionDestination, collidesWithObstacles as motionCollidesWithObstacles, intersectsObstacle as motionIntersectsObstacle } from "../movement/motionController";

export interface SceneGeometryObstacleBounds {
  position: Vec2;
  size: Vec2;
}

export interface FindDashDestinationInput {
  position: Vec2;
  direction: Vec2;
  distance: number;
  radius: number;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

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

export function collidesWithObstacles(
  position: Vec2,
  radius: number,
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): boolean {
  return motionCollidesWithObstacles(position, radius, obstacleBounds);
}

export function createObstacleCollisionAdapter(
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): (position: Vec2, radius: number) => boolean {
  return (position, radius) => collidesWithObstacles(position, radius, obstacleBounds);
}

export function intersectsObstacle(position: Vec2, radius: number, obstacle: SceneGeometryObstacleBounds): boolean {
  return motionIntersectsObstacle(position, radius, obstacle);
}
