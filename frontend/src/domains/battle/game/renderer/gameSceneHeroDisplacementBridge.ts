import type { Hero, Vec2 } from "../../objects/types";
import { applyKnockbackDisplacement, applyRecoilDisplacement } from "../../runtime/local/geometry/heroDisplacementAdapter";
import type { SceneGeometryObstacleBounds } from "../../runtime/local/geometry/sceneGeometry";

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

export function createGameSceneHeroDisplacementBridge(
  options: GameSceneHeroDisplacementBridgeOptions
): GameSceneHeroDisplacementBridge {
  return {
    applyKnockback: (hero, direction, strength) => {
      applyKnockbackDisplacement({
        hero,
        direction,
        strength,
        worldSize: options.getWorldSize(),
        obstacleBounds: options.getObstacleBounds(),
        setHeroPosition: (position) => options.setHeroPosition(hero, position)
      });
    },
    applyRecoil: (direction, strength) => {
      const player = options.getPlayerHero();
      applyRecoilDisplacement({
        hero: player,
        direction,
        strength,
        worldSize: options.getWorldSize(),
        obstacleBounds: options.getObstacleBounds(),
        setHeroPosition: (position) => options.setHeroPosition(player, position)
      });
    }
  };
}
