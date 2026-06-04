import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { CombatProjectileEffect } from "../../../../microservices/combat/functions/BattleProjectileImpactRules";

export interface CombatProjectileEffectPresenterCallbacks {
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  flashHero(heroId: string, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
  stopPlayerMotion(): void;
  setPlayerActorDisabled(): void;
  applyKnockback(heroId: string, direction: Vec2, strength: number): void;
  pushEvent(type: GameSnapshot["events"][number]["type"], message: string): void;
}

export interface PresentCombatProjectileEffectInput {
  effect: CombatProjectileEffect;
  snapshot: GameSnapshot;
  callbacks: CombatProjectileEffectPresenterCallbacks;
}

export interface ResolveCombatProjectileEffectPresentationPlanInput {
  effect: CombatProjectileEffect;
  snapshot: GameSnapshot;
}

export type CombatProjectileEffectPresentationAction =
  | {
      kind: "pulse";
      position: Vec2;
      radius: number;
      color: number;
    }
  | {
      kind: "impactSpark";
      position: Vec2;
      color: number;
    }
  | {
      kind: "shockwave";
      position: Vec2;
      startRadius: number;
      endRadius: number;
      color: number;
      durationMs: number;
    }
  | {
      kind: "floatingText";
      position: Vec2;
      text: string;
      color: string;
    }
  | {
      kind: "flashHero";
      heroId: string;
      color: number;
    }
  | {
      kind: "pushEvent";
      eventType: GameSnapshot["events"][number]["type"];
      message: string;
    }
  | {
      kind: "shakeCamera";
      durationMs: number;
      intensity: number;
    }
  | {
      kind: "stopPlayerMotion";
    }
  | {
      kind: "setPlayerActorDisabled";
    }
  | {
      kind: "knockback";
      heroId: string;
      direction: Vec2;
      strength: number;
    };

export interface CombatProjectileEffectPresentationPlan {
  actions: readonly CombatProjectileEffectPresentationAction[];
}
