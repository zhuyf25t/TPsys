import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ProjectileViewState } from "./objects/ProjectileViewObjects";

export function getProjectileDisplayPositionFromViews(
  worldViews: Pick<ProjectileViewState, "projectileViews">,
  projectileId: string
): Vec2 | null {
  const view = worldViews.projectileViews.get(projectileId);
  if (!view?.sprite.active || !view.sprite.visible) {
    return null;
  }

  return { x: view.sprite.x, y: view.sprite.y };
}
