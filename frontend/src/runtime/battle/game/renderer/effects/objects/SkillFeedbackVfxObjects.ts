import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { SkillFeedbackIntent as BattleSkillFeedbackIntent } from "../../../../microservices/abilities/functions/BattleSkillRuntimeProfiles";

export type SkillFeedbackIntent = BattleSkillFeedbackIntent;
export type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
export type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;

export interface SkillFeedbackVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
}

export type SkillFeedbackGraphicsCommandPlan =
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
      kind: "strokeDiamond";
      radius: number;
    }
  | {
      kind: "lineBetween";
      x1: number;
      y1: number;
      x2: number;
      y2: number;
    };

export interface SkillFeedbackGraphicsVfxPlan {
  position: Vec2;
  depth: number;
  scale: number;
  commands: readonly SkillFeedbackGraphicsCommandPlan[];
}

export interface SkillFeedbackRectangleVfxShapePlan {
  position: Vec2;
  width: number;
  height: number;
  color: number;
  alpha: number;
  origin: Vec2;
  rotation: number;
  depth: number;
}

export interface SkillFeedbackTweenPlan {
  x?: number;
  y?: number;
  alpha?: number;
  scale?: number;
  scaleX?: number;
  scaleY?: number;
  rotation?: number;
  durationMs: number;
  ease: string;
}

export interface SkillFeedbackShapeTweenPlan<TShape> {
  shape: TShape;
  tween: SkillFeedbackTweenPlan;
}

export interface SkillFeedbackGraphicsTweenPlan {
  graphics: SkillFeedbackGraphicsVfxPlan;
  tween: SkillFeedbackTweenPlan;
}

export interface SkillBlinkFeedbackVfxPlanInput {
  position: Vec2;
  intent: SkillFeedbackIntent;
  direction: Vec2;
}

export interface SkillBlinkFeedbackVfxPlan {
  marker: SkillFeedbackGraphicsTweenPlan;
}

export interface SkillDashFeedbackVfxPlanInput {
  position: Vec2;
  direction: Vec2;
}

export interface SkillDashFeedbackVfxPlan {
  ring: SkillFeedbackGraphicsTweenPlan;
  streaks: readonly SkillFeedbackShapeTweenPlan<SkillFeedbackRectangleVfxShapePlan>[];
}

export interface SkillFreezeFeedbackRandomSamplingPlanInput {
  intent: SkillFeedbackIntent;
}

export interface SkillFreezeFeedbackRandomSamplingPlan {
  shardCount: number;
  minInnerRadiusScale: number;
  maxInnerRadiusScale: number;
  minOuterRadiusScale: number;
  maxOuterRadiusScale: number;
}

export interface SkillFreezeFeedbackSample {
  innerRadiusScale: number;
  outerRadiusScale: number;
}

export interface SkillFreezeFeedbackVfxPlanInput {
  position: Vec2;
  intent: SkillFeedbackIntent;
  samples: readonly SkillFreezeFeedbackSample[];
}

export interface SkillFreezeFeedbackVfxPlan {
  marker: SkillFeedbackGraphicsTweenPlan;
}

export interface SkillRejectionFeedbackVfxPlanInput {
  position: Vec2;
  radius: number;
}

export interface SkillRejectionFeedbackVfxPlan {
  marker: SkillFeedbackGraphicsTweenPlan;
}
