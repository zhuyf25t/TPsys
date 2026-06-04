import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { OccludableView } from "./ArenaBuilderObjects";

export type OcclusionAlphaHero = Pick<Hero, "position" | "radius" | "alive">;

export type OcclusionProbeShape = Phaser.Geom.Rectangle;

export interface OcclusionAlphaInput {
  player: OcclusionAlphaHero;
  heroes: readonly OcclusionAlphaHero[];
  occludables: readonly OccludableView[];
}

export interface OcclusionProbePlan {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface OcclusionAlphaPlan {
  occludable: OccludableView;
  alpha: number;
}

export interface ResolveOcclusionAlphaPlansInput extends OcclusionAlphaInput {
  probe: OcclusionProbeShape;
  intersectsProbe: (bounds: OcclusionProbeShape, probe: OcclusionProbeShape) => boolean;
  lerpAlpha: (current: number, target: number, amount: number) => number;
}
