import type { Vec2 } from "../objects/types";

export const AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE = 4;

export interface ProjectileBirthPositionInput {
  ownerPosition: Vec2;
  direction: Vec2;
  ownerRadius: number;
  projectileRadius: number;
}

export function resolveProjectileBirthForwardDistance(ownerRadius: number, projectileRadius: number): number {
  return ownerRadius + projectileRadius + AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE;
}

export function resolveProjectileBirthPosition(input: ProjectileBirthPositionInput): Vec2 {
  const forwardDistance = resolveProjectileBirthForwardDistance(input.ownerRadius, input.projectileRadius);
  return {
    x: input.ownerPosition.x + input.direction.x * forwardDistance,
    y: input.ownerPosition.y + input.direction.y * forwardDistance
  };
}
