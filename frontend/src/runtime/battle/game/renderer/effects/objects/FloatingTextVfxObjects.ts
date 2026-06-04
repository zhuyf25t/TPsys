import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export type FloatingTone = "neutral" | "success" | "warning" | "error";

export type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
export type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;

export interface FloatingTextVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
}

export interface FloatingTextStylePlan {
  fontFamily: string;
  fontSize: string;
  color: string;
  strokeColor: string;
  strokeThickness: number;
  origin: Vec2;
  depth: number;
}

export interface FloatingTextCreationPlan {
  position: Vec2;
  text: string;
  style: FloatingTextStylePlan;
}

export interface FloatingTextTweenPlan {
  y: number;
  alpha: number;
  durationMs: number;
  ease: string;
}
