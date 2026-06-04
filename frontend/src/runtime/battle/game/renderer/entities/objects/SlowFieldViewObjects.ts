import type Phaser from "phaser";
import type { BattleSlowFieldState as SlowField } from "../../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";

export interface SlowFieldView {
  fill: Phaser.GameObjects.Arc;
  rim: Phaser.GameObjects.Arc;
}

export interface SlowFieldViewState {
  slowFieldViews: Map<string, SlowFieldView>;
  scratchLiveSlowFieldIds: Set<string>;
}

export interface SlowFieldViewSyncContext {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: SlowFieldViewState;
}

export interface ResolveSlowFieldViewPlanInput {
  field: SlowField;
}

export interface SlowFieldViewCreationPlan {
  fill: SlowFieldCircleCreationPlan;
  rim: SlowFieldCircleCreationPlan;
}

export interface SlowFieldCircleCreationPlan {
  position: Vec2;
  radius: number;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  stroke?: SlowFieldStrokePlan;
}

export interface SlowFieldViewVisualPlan {
  fill: SlowFieldCircleVisualPlan;
  rim: SlowFieldCircleVisualPlan;
}

export interface SlowFieldViewReleasePlan {
  fill: SlowFieldCircleReleasePlan;
  rim: SlowFieldCircleReleasePlan;
}

export interface SlowFieldCircleVisualPlan {
  position: Vec2;
  radius: number;
  fill?: SlowFieldFillPlan;
  stroke?: SlowFieldStrokePlan;
}

export interface SlowFieldCircleReleasePlan {
  destroy: boolean;
}

export interface SlowFieldFillPlan {
  color: number;
  alpha: number;
}

export interface SlowFieldStrokePlan {
  width: number;
  color: number;
  alpha: number;
}
