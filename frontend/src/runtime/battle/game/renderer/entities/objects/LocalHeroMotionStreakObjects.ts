import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface LocalHeroMotionStreakView {
  streaks: Phaser.GameObjects.Rectangle[];
  lastPosition: Vec2 | null;
  lastAngle: number;
  intensity: number;
}

export interface ResolveLocalHeroMotionStreakCreationPlansInput {
  position: Vec2;
}

export interface LocalHeroMotionStreakCreationPlan {
  position: Vec2;
  width: number;
  height: number;
  fillColor: number;
  fillAlpha: number;
  origin: Vec2;
  depth: number;
  visible: boolean;
}

export interface ResolveLocalHeroMotionStreakUpdateInput {
  previousPosition: Vec2 | null;
  displayPosition: Vec2;
  deltaMs: number;
  previousAngle: number;
  previousIntensity: number;
}

export interface LocalHeroMotionStreakUpdate {
  lastPosition: Vec2;
  angle: number;
  intensity: number;
  visible: boolean;
}

export interface ResolveLocalHeroMotionStreakRenderPlanInput {
  displayPosition: Vec2;
  angle: number;
  intensity: number;
  index: number;
}

export interface LocalHeroMotionStreakRenderPlan {
  visible: true;
  position: Vec2;
  rotation: number;
  width: number;
  height: number;
  fillColor: number;
  alpha: number;
}

export interface LocalHeroMotionStreakHiddenPlan {
  visible: false;
  fillColor: number;
  fillAlpha: number;
}
