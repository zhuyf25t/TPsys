import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface ObstacleSkinStrokePlan {
  width: number;
  color: number;
  alpha: number;
}

export interface ObstacleSkinRectanglePlan {
  position: Vec2;
  size: Vec2;
  color: number;
  alpha: number;
  depth: number;
  stroke?: ObstacleSkinStrokePlan;
}
