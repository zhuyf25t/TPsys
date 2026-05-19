import type { Vec2 } from "../../../objects/types";
import { findDashDestination, normalizeVector, type SceneGeometryObstacleBounds } from "./sceneGeometry";

export interface ResolveDisplacementInput {
  position: Vec2;
  radius: number;
  direction: Vec2;
  strength: number;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

function resolveDisplacementDestination(
  position: Vec2,
  radius: number,
  direction: Vec2,
  distance: number,
  worldSize: Vec2,
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): Vec2 | null {
  const normalized = normalizeVector(direction);
  if (normalized.x === 0 && normalized.y === 0) {
    return null;
  }

  return findDashDestination({
    position,
    direction: normalized,
    distance,
    radius,
    worldSize,
    obstacleBounds
  });
}

export function resolveRecoilDestination(input: ResolveDisplacementInput): Vec2 | null {
  const recoilDistance = Math.min(24, input.strength * 0.18);
  return resolveDisplacementDestination(
    input.position,
    input.radius,
    { x: -input.direction.x, y: -input.direction.y },
    recoilDistance,
    input.worldSize,
    input.obstacleBounds
  );
}

export function resolveKnockbackDestination(input: ResolveDisplacementInput): Vec2 | null {
  const knockbackDistance = Math.min(28, input.strength * 0.14);
  return resolveDisplacementDestination(
    input.position,
    input.radius,
    input.direction,
    knockbackDistance,
    input.worldSize,
    input.obstacleBounds
  );
}
