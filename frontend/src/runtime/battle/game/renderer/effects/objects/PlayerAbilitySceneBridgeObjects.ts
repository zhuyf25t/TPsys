import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { SceneGeometryObstacleBounds } from "../../../../local/geometry/sceneGeometry";
import type { HeroView } from "../../entities/worldViewFactory";
import type { FloatingTone } from "./FloatingTextVfxObjects";
import type { PlayerMotionType } from "./PlayerMotionTweenObjects";

export interface PlayerAbilitySceneBridgeOptions {
  getPlayerHero(): Hero;
  getWorldSize(): Vec2;
  getObstacleBounds(): readonly SceneGeometryObstacleBounds[];
  getHeroViews(): ReadonlyMap<string, HeroView>;
  getBaseHeroScale(heroId: string): number;
  isPlayerMotionActive(): boolean;
  startPlayerMotion(destination: Vec2, durationMs: number, motionType: PlayerMotionType): void;
  createAfterimage(
    position: Vec2,
    rotation: number,
    scale: number,
    textureKey: string,
    tint: number,
    alpha: number
  ): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  showFloatingText(position: Vec2, text: string, tone: FloatingTone): void;
  addFreezeField(ownerHeroId: string, position: Vec2, radius: number, durationMs: number): void;
}

export interface ResolvePlayerAbilityTextureKeyInput {
  heroId: string;
  heroViews: ReadonlyMap<string, HeroView>;
}
