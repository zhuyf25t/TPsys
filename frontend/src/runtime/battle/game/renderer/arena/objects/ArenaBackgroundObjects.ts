import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export type ArenaBackgroundTextureRole = "floor" | "outside" | "stone" | "stoneTrim";

export interface NaturalMapPresentationPalette {
  outerBackground: number;
  playableBackground: number;
  cropStroke: number;
  leftBuffer: number;
  rightBuffer: number;
  groundSpecks: readonly number[];
  edgeAccent: number;
}

export interface ArenaBackgroundStrokePlan {
  width: number;
  color: number;
  alpha: number;
}

export interface ArenaBackgroundRectanglePlan {
  position: Vec2;
  size: Vec2;
  color: number;
  alpha: number;
  depth: number;
  rotation?: number;
  stroke?: ArenaBackgroundStrokePlan;
}

export interface ArenaBackgroundEllipsePlan {
  position: Vec2;
  size: Vec2;
  color: number;
  alpha: number;
  depth: number;
  rotation?: number;
}

export interface ArenaNaturalGroundTexturePlan {
  specks: readonly ArenaBackgroundEllipsePlan[];
  edgeAccents: readonly ArenaBackgroundRectanglePlan[];
}

export interface ArenaNaturalTerrainPatchPlan {
  rectangles: readonly ArenaBackgroundRectanglePlan[];
  ellipses: readonly ArenaBackgroundEllipsePlan[];
}

export interface ArenaBackgroundPatternPlan {
  position: Vec2;
  size: Vec2;
  textureRole: ArenaBackgroundTextureRole;
  depth: number;
  alpha: number;
  tint?: number;
}
