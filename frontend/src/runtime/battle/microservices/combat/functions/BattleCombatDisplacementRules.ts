import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  findMotionDestination,
  normalizeVector,
  type MotionObstacleBounds
} from "../../world/functions/BattleMotionRules";

export interface BattleCombatDisplacementInput {
  position: Vec2;
  radius: number;
  direction: Vec2;
  strength: number;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}

function resolveCombatDisplacementDestination(
  position: Vec2,
  radius: number,
  direction: Vec2,
  distance: number,
  worldSize: Vec2,
  obstacleBounds: readonly MotionObstacleBounds[]
): Vec2 | null {
  const normalized = normalizeVector(direction);
  if ((normalized.x === 0 && normalized.y === 0) || distance <= 0) {
    return null;
  }

  return findMotionDestination({
    position,
    direction: normalized,
    distance,
    radius,
    worldSize,
    obstacleBounds
  }).destination;
}

export function resolveRecoilDestination(input: BattleCombatDisplacementInput): Vec2 | null {
  const recoilDistance = Math.min(24, Math.max(0, input.strength) * 0.18);
  return resolveCombatDisplacementDestination(
    input.position,
    input.radius,
    { x: -input.direction.x, y: -input.direction.y },
    recoilDistance,
    input.worldSize,
    input.obstacleBounds
  );
}

export function resolveKnockbackDestination(input: BattleCombatDisplacementInput): Vec2 | null {
  const knockbackDistance = Math.min(28, Math.max(0, input.strength) * 0.14);
  return resolveCombatDisplacementDestination(
    input.position,
    input.radius,
    input.direction,
    knockbackDistance,
    input.worldSize,
    input.obstacleBounds
  );
}
