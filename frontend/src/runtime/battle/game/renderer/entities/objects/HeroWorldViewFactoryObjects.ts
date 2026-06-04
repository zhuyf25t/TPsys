import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface CreateHeroWorldViewInput {
  scene: Phaser.Scene;
  hero: Hero;
  playerHeroId: string;
  getBaseHeroScale: (heroId: string) => number;
}

export interface ResolveHeroWorldViewCreationPlanInput {
  hero: Hero;
  playerHeroId: string;
  baseHeroScale: number;
}

export interface HeroWorldViewNameLabelStyle {
  fontFamily: "Segoe UI";
  fontSize: string;
  color: string;
}

export interface HeroWorldViewCreationPlan {
  isPlayer: boolean;
  spriteDepth: number;
  textureKey: string;
  tint: number;
  baseScale: number;
  nameLabel: HeroWorldViewTextCreationPlan;
  healthBackground: HeroWorldViewRectangleCreationPlan;
  healthFill: HeroWorldViewRectangleCreationPlan;
  actionBackground: HeroWorldViewStrokeRectangleCreationPlan;
  actionFill: HeroWorldViewRectangleCreationPlan;
}

export interface HeroWorldViewTextCreationPlan {
  position: Vec2;
  text: string;
  style: HeroWorldViewNameLabelStyle;
  origin: Vec2;
  depth: number;
}

export interface HeroWorldViewRectangleCreationPlan {
  position: Vec2;
  size: Vec2;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  origin?: Vec2;
  visible: boolean;
}

export interface HeroWorldViewStrokeRectangleCreationPlan extends HeroWorldViewRectangleCreationPlan {
  stroke: HeroWorldViewStrokeStyle | null;
}

export interface HeroWorldViewStrokeStyle {
  width: number;
  color: number;
  alpha: number;
}
