import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";

export interface LocalAuthoritativeHeroCorrectionInput {
  currentPosition: Vec2;
  authoritativePosition: Vec2;
  context?: LocalAuthoritativeHeroCorrectionContext;
}

export interface LocalAuthoritativeHeroCorrectionContext {
  localMovementActive?: boolean;
  forceHardSnap?: boolean;
}

export type LocalAuthoritativeHeroCorrectionMode = "none" | "deadzone" | "hardSnap" | "smooth";

export interface LocalAuthoritativeHeroCorrectionResult {
  nextPosition: Vec2;
  targetPosition: Vec2 | null;
  mode: LocalAuthoritativeHeroCorrectionMode;
  hardSnap: boolean;
  applied: boolean;
  ignoredByDeadzone: boolean;
}

export interface LocalAuthoritativeHeroCorrectionTuning {
  deadzone: number;
  halfLifeMs: number;
}

const LOCAL_AUTHORITATIVE_HARD_SNAP_DISTANCE = 320;
const LOCAL_AUTHORITATIVE_STATIONARY_CORRECTION = {
  deadzone: 10,
  halfLifeMs: 180
} as const;
const LOCAL_AUTHORITATIVE_MOVING_CORRECTION = {
  deadzone: 42,
  halfLifeMs: 320
} as const;

export function resolveLocalAuthoritativeHeroCorrection(
  input: LocalAuthoritativeHeroCorrectionInput
): LocalAuthoritativeHeroCorrectionResult {
  if (!isFinitePosition(input.authoritativePosition)) {
    return {
      nextPosition: clonePosition(input.currentPosition),
      targetPosition: null,
      mode: "none",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: false
    };
  }

  if (!isFinitePosition(input.currentPosition) || input.context?.forceHardSnap === true) {
    return {
      nextPosition: clonePosition(input.authoritativePosition),
      targetPosition: clonePosition(input.authoritativePosition),
      mode: "hardSnap",
      hardSnap: true,
      applied: true,
      ignoredByDeadzone: false
    };
  }

  const deltaX = input.authoritativePosition.x - input.currentPosition.x;
  const deltaY = input.authoritativePosition.y - input.currentPosition.y;
  const distance = Math.hypot(deltaX, deltaY);
  const correction = selectLocalAuthoritativeHeroCorrectionTuning(input.context?.localMovementActive === true);

  if (distance <= 0.001) {
    return {
      nextPosition: clonePosition(input.currentPosition),
      targetPosition: null,
      mode: "none",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: false
    };
  }

  if (distance >= LOCAL_AUTHORITATIVE_HARD_SNAP_DISTANCE) {
    return {
      nextPosition: clonePosition(input.authoritativePosition),
      targetPosition: clonePosition(input.authoritativePosition),
      mode: "hardSnap",
      hardSnap: true,
      applied: true,
      ignoredByDeadzone: false
    };
  }

  if (distance <= correction.deadzone) {
    return {
      nextPosition: clonePosition(input.currentPosition),
      targetPosition: null,
      mode: "deadzone",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: true
    };
  }

  return {
    nextPosition: clonePosition(input.currentPosition),
    targetPosition: clonePosition(input.authoritativePosition),
    mode: "smooth",
    hardSnap: false,
    applied: true,
    ignoredByDeadzone: false
  };
}

export function selectLocalAuthoritativeHeroCorrectionTuning(
  localMovementActive: boolean
): LocalAuthoritativeHeroCorrectionTuning {
  return localMovementActive ? LOCAL_AUTHORITATIVE_MOVING_CORRECTION : LOCAL_AUTHORITATIVE_STATIONARY_CORRECTION;
}

export function isFiniteLocalAuthoritativeCorrectionPosition(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function isFinitePosition(position: Vec2): boolean {
  return isFiniteLocalAuthoritativeCorrectionPosition(position);
}

function clonePosition(position: Vec2): Vec2 {
  return { x: position.x, y: position.y };
}
