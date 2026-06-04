import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface SceneRingPulseEffect {
  circle: Phaser.GameObjects.Arc;
  ttlMs: number;
  maxTtlMs: number;
}

export interface SceneRingPulsePlanInput {
  position: Vec2;
  radius: number;
  color: number;
}

export interface SceneRingPulseShapePlan {
  position: Vec2;
  radius: number;
  color: number;
  fillAlpha: number;
  depth: number;
  strokeWidth: number;
  strokeColor: number;
  strokeAlpha: number;
}

export interface SceneRingPulseLifetimePlan {
  ttlMs: number;
  maxTtlMs: number;
}

export interface SceneRingPulsePlan {
  shape: SceneRingPulseShapePlan;
  lifetime: SceneRingPulseLifetimePlan;
}

export interface SceneRingPulseUpdatePlanInput {
  ttlMs: number;
  maxTtlMs: number;
  deltaMs: number;
}

export type SceneRingPulseUpdatePlan =
  | {
      kind: "destroy";
    }
  | {
      kind: "update";
      ttlMs: number;
      scale: number;
      alpha: number;
    };
