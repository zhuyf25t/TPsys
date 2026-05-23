import type { Vec2 } from "../../objects/types";
import type { LocalHeroDisplayPositionStore } from "./localHeroDisplayPose";
import { recordLocalHeroCorrectionDiagnostics } from "./localHeroCorrectionDiagnostics";

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

const LOCAL_AUTHORITATIVE_HARD_SNAP_DISTANCE = 320;
const LOCAL_AUTHORITATIVE_STATIONARY_CORRECTION = {
  deadzone: 10,
  halfLifeMs: 180
} as const;
const LOCAL_AUTHORITATIVE_MOVING_CORRECTION = {
  deadzone: 42,
  halfLifeMs: 320
} as const;

interface LocalAuthoritativeHeroCorrectionTuning {
  deadzone: number;
  halfLifeMs: number;
}

interface PendingLocalAuthoritativeCorrection {
  targetPosition: Vec2;
}

export class LocalAuthoritativeHeroCorrectionController {
  private pendingCorrection: PendingLocalAuthoritativeCorrection | null = null;

  public constructor(private readonly displayPoseStore: LocalHeroDisplayPositionStore) {}

  public observeAuthoritativePosition(
    authoritativePosition: Vec2,
    context: LocalAuthoritativeHeroCorrectionContext = {}
  ): LocalAuthoritativeHeroCorrectionResult {
    const currentPosition = this.displayPoseStore.read().position;
    const correction = resolveLocalAuthoritativeHeroCorrection({
      currentPosition,
      authoritativePosition,
      context
    });

    recordLocalHeroCorrectionDiagnostics({
      currentPosition,
      authoritativePosition,
      nextPosition: correction.nextPosition,
      hardSnap: correction.hardSnap,
      applied: correction.applied,
      ignoredByDeadzone: correction.ignoredByDeadzone,
      mode: correction.mode
    });

    if (correction.hardSnap) {
      this.pendingCorrection = null;
      this.displayPoseStore.writePosition(correction.nextPosition);
      return correction;
    }

    if (correction.mode === "smooth" && correction.targetPosition) {
      this.pendingCorrection = {
        targetPosition: {
          x: correction.targetPosition.x,
          y: correction.targetPosition.y
        }
      };
      return correction;
    }

    if (correction.ignoredByDeadzone || correction.mode === "none") {
      this.pendingCorrection = null;
    }

    return correction;
  }

  public update(deltaMs: number, context: LocalAuthoritativeHeroCorrectionContext = {}): void {
    if (!this.pendingCorrection) {
      return;
    }

    const currentPosition = this.displayPoseStore.read().position;
    const targetPosition = this.pendingCorrection.targetPosition;
    if (!isFinitePosition(currentPosition) || !isFinitePosition(targetPosition)) {
      this.pendingCorrection = null;
      return;
    }

    const correction = selectCorrectionTuning(context.localMovementActive === true);
    const distance = distanceBetween(currentPosition, targetPosition);
    if (distance <= correction.deadzone) {
      this.pendingCorrection = null;
      return;
    }

    const safeDeltaMs = Math.max(0, deltaMs);
    if (!Number.isFinite(correction.halfLifeMs) || correction.halfLifeMs <= 0) {
      this.pendingCorrection = null;
      return;
    }

    const alpha = 1 - Math.exp((-Math.LN2 * safeDeltaMs) / correction.halfLifeMs);
    if (alpha <= 0) {
      return;
    }

    const nextPosition = {
      x: currentPosition.x + (targetPosition.x - currentPosition.x) * alpha,
      y: currentPosition.y + (targetPosition.y - currentPosition.y) * alpha
    };
    this.displayPoseStore.writePosition(nextPosition);

    if (distanceBetween(nextPosition, targetPosition) <= correction.deadzone) {
      this.pendingCorrection = null;
    }
  }
}

/** 中文名：解析本地authoritative英雄correction（resolveLocalAuthoritativeHeroCorrection）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveLocalAuthoritativeHeroCorrection(
  input: LocalAuthoritativeHeroCorrectionInput
): LocalAuthoritativeHeroCorrectionResult {
  if (!isFinitePosition(input.authoritativePosition)) {
    return {
      nextPosition: {
        x: input.currentPosition.x,
        y: input.currentPosition.y
      },
      targetPosition: null,
      mode: "none",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: false
    };
  }

  if (!isFinitePosition(input.currentPosition) || input.context?.forceHardSnap === true) {
    return {
      nextPosition: {
        x: input.authoritativePosition.x,
        y: input.authoritativePosition.y
      },
      targetPosition: {
        x: input.authoritativePosition.x,
        y: input.authoritativePosition.y
      },
      mode: "hardSnap",
      hardSnap: true,
      applied: true,
      ignoredByDeadzone: false
    };
  }

  const deltaX = input.authoritativePosition.x - input.currentPosition.x;
  const deltaY = input.authoritativePosition.y - input.currentPosition.y;
  const distance = Math.hypot(deltaX, deltaY);
  const correction = selectCorrectionTuning(input.context?.localMovementActive === true);

  if (distance <= 0.001) {
    return {
      nextPosition: {
        x: input.currentPosition.x,
        y: input.currentPosition.y
      },
      targetPosition: null,
      mode: "none",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: false
    };
  }

  if (distance >= LOCAL_AUTHORITATIVE_HARD_SNAP_DISTANCE) {
    return {
      nextPosition: {
        x: input.authoritativePosition.x,
        y: input.authoritativePosition.y
      },
      targetPosition: {
        x: input.authoritativePosition.x,
        y: input.authoritativePosition.y
      },
      mode: "hardSnap",
      hardSnap: true,
      applied: true,
      ignoredByDeadzone: false
    };
  }

  if (distance <= correction.deadzone) {
    return {
      nextPosition: {
        x: input.currentPosition.x,
        y: input.currentPosition.y
      },
      targetPosition: null,
      mode: "deadzone",
      hardSnap: false,
      applied: false,
      ignoredByDeadzone: true
    };
  }

  return {
    nextPosition: {
      x: input.currentPosition.x,
      y: input.currentPosition.y
    },
    targetPosition: {
      x: input.authoritativePosition.x,
      y: input.authoritativePosition.y
    },
    mode: "smooth",
    hardSnap: false,
    applied: true,
    ignoredByDeadzone: false
  };
}

function selectCorrectionTuning(localMovementActive: boolean): LocalAuthoritativeHeroCorrectionTuning {
  return localMovementActive ? LOCAL_AUTHORITATIVE_MOVING_CORRECTION : LOCAL_AUTHORITATIVE_STATIONARY_CORRECTION;
}

function isFinitePosition(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
