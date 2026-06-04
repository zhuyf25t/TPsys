import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface WorldViewIndicatorStyle {
  position: Vec2;
  radius: number;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  visible: boolean;
  strokeWidth: number;
  strokeColor: number;
  strokeAlpha: number;
}

export interface WorldViewIndicatorCreationPlan {
  rangeIndicator: WorldViewIndicatorStyle;
  targetIndicator: WorldViewIndicatorStyle;
}

export interface WorldViewIndicatorViews {
  rangeIndicator: Phaser.GameObjects.Arc;
  targetIndicator: Phaser.GameObjects.Arc;
}
