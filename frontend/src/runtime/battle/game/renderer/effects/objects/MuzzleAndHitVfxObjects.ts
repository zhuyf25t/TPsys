import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
export type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;
export type CreateRingPulse = (position: Vec2, radius: number, color: number) => void;

export interface MuzzleAndHitVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
  createRingPulse: CreateRingPulse;
}

export interface MuzzleAndHitStrokePlan {
  width: number;
  color: number;
  alpha: number;
}

export interface MuzzleAndHitCircleVfxShapePlan {
  position: Vec2;
  radius: number;
  color: number;
  fillAlpha: number;
  depth: number;
  stroke?: MuzzleAndHitStrokePlan;
}

export interface MuzzleAndHitRectangleVfxShapePlan {
  position: Vec2;
  width: number;
  height: number;
  color: number;
  alpha: number;
  origin: Vec2;
  rotation: number;
  depth: number;
}

export interface MuzzleAndHitTweenPlan {
  x?: number;
  y?: number;
  alpha?: number;
  scale?: number;
  scaleX?: number;
  scaleY?: number;
  durationMs: number;
  ease: string;
}

export interface MuzzleAndHitShapeTweenPlan<TShape> {
  shape: TShape;
  tween: MuzzleAndHitTweenPlan;
}

export type MuzzleAndHitGraphicsCommandPlan =
  | {
      kind: "lineStyle";
      width: number;
      color: number;
      alpha: number;
    }
  | {
      kind: "fillStyle";
      color: number;
      alpha: number;
    }
  | {
      kind: "strokeCircle";
      x: number;
      y: number;
      radius: number;
    }
  | {
      kind: "fillCircle";
      x: number;
      y: number;
      radius: number;
    }
  | {
      kind: "lineBetween";
      x1: number;
      y1: number;
      x2: number;
      y2: number;
    };

export interface MuzzleAndHitGraphicsVfxPlan {
  position: Vec2;
  depth: number;
  commands: readonly MuzzleAndHitGraphicsCommandPlan[];
}

export interface MuzzleAndHitHitConfirmVfxPlanInput {
  position: Vec2;
  color: number;
}

export interface MuzzleAndHitHitConfirmVfxPlan {
  graphics: MuzzleAndHitGraphicsVfxPlan;
  tween: MuzzleAndHitTweenPlan;
}

export interface MuzzleAndHitImpactSparkRandomSamplingPlan {
  sparkCount: number;
  minAngleJitterRadians: number;
  maxAngleJitterRadians: number;
  minSparkLength: number;
  maxSparkLength: number;
  minTravelDistance: number;
  maxTravelDistance: number;
}

export interface MuzzleAndHitImpactSparkSample {
  angleJitterRadians: number;
  sparkLength: number;
  xTravelDistance: number;
  yTravelDistance: number;
}

export interface MuzzleAndHitImpactSparkVfxPlanInput {
  position: Vec2;
  color: number;
  samples: readonly MuzzleAndHitImpactSparkSample[];
}

export interface MuzzleAndHitImpactSparkVfxPlan {
  burst: MuzzleAndHitShapeTweenPlan<MuzzleAndHitCircleVfxShapePlan>;
  sparks: readonly MuzzleAndHitShapeTweenPlan<MuzzleAndHitRectangleVfxShapePlan>[];
}

export interface MuzzleAndHitRingPulsePlan {
  position: Vec2;
  radius: number;
  color: number;
}

export interface MuzzleAndHitMuzzleBurstSamplingPlanInput {
  sparks: number;
  radius: number;
}

export interface MuzzleAndHitMuzzleBurstRandomSamplingPlan {
  sparkCount: number;
  minSpread: number;
  maxSpread: number;
  minDistance: number;
  maxDistance: number;
  distanceRadiusBonus: number;
  minLateralDrift: number;
  maxLateralDrift: number;
  minSparkLength: number;
  maxSparkLength: number;
  minDurationJitterMs: number;
  maxDurationJitterMs: number;
}

export interface MuzzleAndHitMuzzleBurstSample {
  spread: number;
  distance: number;
  lateralDrift: number;
  sparkLength: number;
  durationJitterMs: number;
}

export interface MuzzleAndHitMuzzleBurstVfxPlanInput {
  position: Vec2;
  color: number;
  radius: number;
  direction: Vec2;
  samples: readonly MuzzleAndHitMuzzleBurstSample[];
}

export interface MuzzleAndHitMuzzleBurstVfxPlan {
  ringPulse: MuzzleAndHitRingPulsePlan;
  core: MuzzleAndHitShapeTweenPlan<MuzzleAndHitCircleVfxShapePlan>;
  flash: MuzzleAndHitShapeTweenPlan<MuzzleAndHitRectangleVfxShapePlan>;
  sparks: readonly MuzzleAndHitShapeTweenPlan<MuzzleAndHitRectangleVfxShapePlan>[];
}

export interface MuzzleAndHitProjectileDissipateVfxPlanInput {
  position: Vec2;
  color: number;
}

export interface MuzzleAndHitProjectileDissipateVfxPlan {
  ring: MuzzleAndHitShapeTweenPlan<MuzzleAndHitCircleVfxShapePlan>;
  mote: MuzzleAndHitShapeTweenPlan<MuzzleAndHitCircleVfxShapePlan>;
}

export interface MuzzleAndHitShockwaveVfxPlanInput {
  position: Vec2;
  startRadius: number;
  endRadius: number;
  color: number;
  durationMs: number;
}

export interface MuzzleAndHitShockwaveShapePlan {
  position: Vec2;
  radius: number;
  color: number;
  fillAlpha: number;
  depth: number;
  strokeWidth: number;
  strokeColor: number;
  strokeAlpha: number;
}

export interface MuzzleAndHitShockwaveTweenPlan {
  scaleX: number;
  scaleY: number;
  alpha: number;
  durationMs: number;
  ease: string;
}

export interface MuzzleAndHitShockwaveVfxPlan {
  shape: MuzzleAndHitShockwaveShapePlan;
  tween: MuzzleAndHitShockwaveTweenPlan;
}
