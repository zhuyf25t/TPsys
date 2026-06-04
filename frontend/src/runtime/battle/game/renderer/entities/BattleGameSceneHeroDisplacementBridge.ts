import { applyKnockbackDisplacement, applyRecoilDisplacement } from "../../../local/geometry/heroDisplacementAdapter";
import type {
  GameSceneHeroDisplacementBridge,
  GameSceneHeroDisplacementBridgeOptions
} from "./objects/BattleGameSceneHeroDisplacementObjects";

export type {
  GameSceneHeroDisplacementBridge,
  GameSceneHeroDisplacementBridgeOptions
} from "./objects/BattleGameSceneHeroDisplacementObjects";

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
