import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export type ArenaDecorationTextureRole = "crate" | "rock" | "stoneTrim" | "wall";

export interface ArenaDecorationStrokePlan {
  width: number;
  color: number;
  alpha: number;
}

export interface ArenaDecorationRectanglePlan {
  position: Vec2;
  size: Vec2;
  color: number;
  alpha: number;
  depth: number;
  stroke?: ArenaDecorationStrokePlan;
}

export interface ArenaDecorationEllipsePlan {
  position: Vec2;
  size: Vec2;
  color: number;
  alpha: number;
  depth: number;
}

export interface ArenaDecorationPatternPlan {
  position: Vec2;
  size: Vec2;
  textureRole: ArenaDecorationTextureRole;
  depth: number;
  alpha: number;
  tint?: number;
}

export interface ArenaDecorationImagePlan {
  position: Vec2;
  textureRole: ArenaDecorationTextureRole;
  scale: number;
  depth: number;
  tint?: number;
  alpha: number;
  occludableBaseAlpha?: number;
}

export type ArenaDecorationElementPlan =
  | {
      kind: "rectangle";
      rectangle: ArenaDecorationRectanglePlan;
    }
  | {
      kind: "image";
      image: ArenaDecorationImagePlan;
    };

export interface ArenaDecorationPresentationPlan {
  elements: readonly ArenaDecorationElementPlan[];
}

export interface ArenaPickupPadPresentationPlan {
  patterns: readonly ArenaDecorationPatternPlan[];
  rectangles: readonly ArenaDecorationRectanglePlan[];
  ellipses: readonly ArenaDecorationEllipsePlan[];
}

export interface NaturalPickupPadPalette {
  weaponShadow: number;
  weaponOuter: number;
  weaponInner: number;
  itemShadow: number;
  itemOuter: number;
  itemInner: number;
}
