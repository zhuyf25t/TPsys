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

/** 中文名：创建gamescene英雄displacementbridge（createGameSceneHeroDisplacementBridge）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
