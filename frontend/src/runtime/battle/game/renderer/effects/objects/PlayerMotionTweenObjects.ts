import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { HeroView } from "../../entities/worldViewFactory";

export type PlayerMotionType = "jump" | "dash" | "blink";

export interface PlayerMotionTweenControllerOptions {
  scene: Phaser.Scene;
  playerActor: Phaser.Physics.Arcade.Image;
  heroViews: Map<string, HeroView>;
  getPlayerHero(): Hero;
  getBaseHeroScale(heroId: string): number;
  createPulse(position: Vec2, radius: number, color: number): void;
}

export interface PlayerMotionTrailFeedbackPlan {
  delayMs: number;
  tint: number;
  alpha: number;
}

export interface PlayerMotionCompletionPulsePlan {
  radius: number;
  color: number;
}

export interface PlayerMotionSpriteTweenPlanInput {
  baseScale: number;
  durationMs: number;
}

export interface PlayerMotionSpriteTweenPlan {
  scaleX: number;
  scaleY: number;
  yoyo: boolean;
  durationMs: number;
  ease: string;
}

export interface PlayerMotionAfterimagePlanInput {
  position: Vec2;
  rotation: number;
  scale: number;
  textureKey: string;
  tint: number;
  alpha: number;
}

export interface PlayerMotionAfterimageShapePlan {
  position: Vec2;
  textureKey: string;
  rotation: number;
  scale: number;
  tint: number;
  alpha: number;
  depth: number;
}

export interface PlayerMotionAfterimageTweenPlan {
  alpha: number;
  scaleX: number;
  scaleY: number;
  durationMs: number;
}

export interface PlayerMotionAfterimagePlan {
  shape: PlayerMotionAfterimageShapePlan;
  tween: PlayerMotionAfterimageTweenPlan;
}
