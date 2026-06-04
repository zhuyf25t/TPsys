import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  isFiniteLocalAuthoritativeCorrectionPosition,
  selectLocalAuthoritativeHeroCorrectionTuning
} from "../../../../local/movement/BattleLocalAuthoritativeHeroCorrectionRules";
import type {
  LocalAuthoritativeHeroCorrectionUpdatePlan,
  PendingLocalAuthoritativeCorrection
} from "../objects/BattleLocalAuthoritativeHeroCorrectionObjects";

export function createPendingLocalAuthoritativeCorrection(targetPosition: Vec2): PendingLocalAuthoritativeCorrection {
  return {
    targetPosition: {
      x: targetPosition.x,
      y: targetPosition.y
    }
  };
}

export function resolveLocalAuthoritativeHeroCorrectionUpdatePlan(input: {
  pendingCorrection: PendingLocalAuthoritativeCorrection;
  currentPosition: Vec2;
  deltaMs: number;
  localMovementActive: boolean;
}): LocalAuthoritativeHeroCorrectionUpdatePlan {
  const targetPosition = input.pendingCorrection.targetPosition;
  if (
    !isFiniteLocalAuthoritativeCorrectionPosition(input.currentPosition) ||
    !isFiniteLocalAuthoritativeCorrectionPosition(targetPosition)
  ) {
    return { kind: "clear-pending" };
  }

  const correction = selectLocalAuthoritativeHeroCorrectionTuning(input.localMovementActive);
  const distance = distanceBetweenLocalAuthoritativeCorrectionPositions(input.currentPosition, targetPosition);
  if (distance <= correction.deadzone) {
    return { kind: "clear-pending" };
  }

  if (!Number.isFinite(correction.halfLifeMs) || correction.halfLifeMs <= 0) {
    return { kind: "clear-pending" };
  }

  const safeDeltaMs = Math.max(0, input.deltaMs);
  const alpha = 1 - Math.exp((-Math.LN2 * safeDeltaMs) / correction.halfLifeMs);
  if (alpha <= 0) {
    return { kind: "keep-pending" };
  }

  const nextPosition = {
    x: input.currentPosition.x + (targetPosition.x - input.currentPosition.x) * alpha,
    y: input.currentPosition.y + (targetPosition.y - input.currentPosition.y) * alpha
  };

  return {
    kind: "write-position",
    nextPosition,
    clearPending:
      distanceBetweenLocalAuthoritativeCorrectionPositions(nextPosition, targetPosition) <= correction.deadzone
  };
}

function distanceBetweenLocalAuthoritativeCorrectionPositions(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
