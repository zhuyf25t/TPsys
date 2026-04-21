import type { GameSnapshot } from "../../../domain/types";
import { INNER_OBSTACLES } from "../../../game/constants";
import type { HudMinimapData, HudMinimapRect } from "../../../ui/Hud";

export interface MinimapPresenterInput {
  snapshot: GameSnapshot;
  cameraRect: HudMinimapRect;
}

export function createMinimapData(input: MinimapPresenterInput): HudMinimapData {
  const { snapshot, cameraRect } = input;
  const clampedCameraX = clamp(cameraRect.x, 0, snapshot.worldSize.x);
  const clampedCameraY = clamp(cameraRect.y, 0, snapshot.worldSize.y);
  const clampedCameraRight = clamp(cameraRect.x + cameraRect.width, 0, snapshot.worldSize.x);
  const clampedCameraBottom = clamp(cameraRect.y + cameraRect.height, 0, snapshot.worldSize.y);

  return {
    worldWidth: snapshot.worldSize.x,
    worldHeight: snapshot.worldSize.y,
    cameraRect: {
      x: clampedCameraX,
      y: clampedCameraY,
      width: clampedCameraRight - clampedCameraX,
      height: clampedCameraBottom - clampedCameraY
    },
    obstacles: INNER_OBSTACLES.map((obstacle) => ({
      x: obstacle.position.x - obstacle.size.x / 2,
      y: obstacle.position.y - obstacle.size.y / 2,
      width: obstacle.size.x,
      height: obstacle.size.y
    })),
    pickups: [
      ...snapshot.weaponPickups.filter((pickup) => pickup.available).map((pickup) => ({
        x: pickup.position.x,
        y: pickup.position.y,
        radius: 2,
        color: "#ffd86d"
      })),
      ...snapshot.itemPickups.filter((pickup) => pickup.available).map((pickup) => ({
        x: pickup.position.x,
        y: pickup.position.y,
        radius: 2,
        color: pickup.kind === "Medkit" ? "#7bff9b" : "#b8d7ef"
      }))
    ],
    heroes: snapshot.heroes
      .filter((hero) => hero.alive)
      .map((hero) => ({
        x: hero.position.x,
        y: hero.position.y,
        radius: hero.heroId === snapshot.playerHeroId ? 3 : 2,
        color: hero.heroId === snapshot.playerHeroId ? "#6ee4ff" : "#ff7f7f"
      }))
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
