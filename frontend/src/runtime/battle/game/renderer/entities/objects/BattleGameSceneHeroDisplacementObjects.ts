import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { SceneGeometryObstacleBounds } from "../../../../local/geometry/sceneGeometry";

export interface GameSceneHeroDisplacementBridgeOptions {
  getWorldSize(): Vec2;
  getObstacleBounds(): readonly SceneGeometryObstacleBounds[];
  getPlayerHero(): Hero;
  setHeroPosition(hero: Hero, position: Vec2): void;
}

export interface GameSceneHeroDisplacementBridge {
  applyKnockback(hero: Hero, direction: Vec2, strength: number): void;
  applyRecoil(direction: Vec2, strength: number): void;
}
