import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { getProjectileDisplayPositionFromViews } from "./projectileDisplayPositionReader";
import type { WorldViewState } from "./objects/WorldViewFactoryObjects";

export function getHeroDisplayPositionFromWorldViews(worldViews: WorldViewState, heroId: string): Vec2 | null {
  const view = worldViews.heroViews.get(heroId);
  if (!view?.sprite.active || !view.sprite.visible) {
    return null;
  }

  return { x: view.sprite.x, y: view.sprite.y };
}

export function getProjectileDisplayPositionFromWorldViews(
  worldViews: WorldViewState,
  projectileId: string
): Vec2 | null {
  return getProjectileDisplayPositionFromViews(worldViews, projectileId);
}
