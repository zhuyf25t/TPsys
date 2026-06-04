import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ProjectileTracerFeedbackOptions } from "../../../../microservices/combat/functions/BattleProjectileFeedbackRules";
import type { BattleProjectileFeedbackEffectPlan } from "../../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";

export interface BattleProjectileFeedbackEffectPresenterCallbacks {
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createProjectileDissipate(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createProjectileTracer(options: ProjectileTracerFeedbackOptions): void;
}

export interface ResolveBattleProjectileFeedbackEffectPresentationActionInput {
  effect: BattleProjectileFeedbackEffectPlan;
}

export type BattleProjectileFeedbackEffectPresentationAction =
  | {
      kind: "impactSpark";
      position: Vec2;
      color: number;
    }
  | {
      kind: "pulse";
      position: Vec2;
      radius: number;
      color: number;
    }
  | {
      kind: "projectileDissipate";
      position: Vec2;
      color: number;
    }
  | {
      kind: "projectileTracer";
      options: ProjectileTracerFeedbackOptions;
    }
  | {
      kind: "shockwave";
      position: Vec2;
      startRadius: number;
      endRadius: number;
      color: number;
      durationMs: number;
    };
