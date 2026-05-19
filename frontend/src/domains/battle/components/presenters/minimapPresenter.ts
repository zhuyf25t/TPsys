import type { GameSnapshot } from "../../objects/types";
import { FLOOR_TILE_SIZE, HERO_RADIUS } from "../../game/constants";
import type { HudMinimapData, HudMinimapRect } from "../../game/ui/Hud";

export interface MinimapObstacleBounds {
  position: {
    x: number;
    y: number;
  };
  size: {
    x: number;
    y: number;
  };
}

export interface MinimapPresenterInput {
  snapshot: GameSnapshot;
  cameraRect: HudMinimapRect;
  obstacleBounds: readonly MinimapObstacleBounds[];
}

/** 中文名：创建minimap数据（createMinimapData）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createMinimapData(input: MinimapPresenterInput): HudMinimapData {
  const { snapshot, cameraRect, obstacleBounds } = input;
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
    obstacles: obstacleBounds.map((obstacle) => ({
      x: obstacle.position.x - obstacle.size.x / 2,
      y: obstacle.position.y - obstacle.size.y / 2,
      width: obstacle.size.x,
      height: obstacle.size.y
    })),
    clearanceObstacles: obstacleBounds.map((obstacle) =>
      clampRectToWorld(
        {
          x: obstacle.position.x - obstacle.size.x / 2 - HERO_RADIUS,
          y: obstacle.position.y - obstacle.size.y / 2 - HERO_RADIUS,
          width: obstacle.size.x + HERO_RADIUS * 2,
          height: obstacle.size.y + HERO_RADIUS * 2
        },
        snapshot.worldSize.x,
        snapshot.worldSize.y
      )
    ),
    centerLimitRect: {
      x: FLOOR_TILE_SIZE + HERO_RADIUS,
      y: FLOOR_TILE_SIZE + HERO_RADIUS,
      width: Math.max(0, snapshot.worldSize.x - (FLOOR_TILE_SIZE + HERO_RADIUS) * 2),
      height: Math.max(0, snapshot.worldSize.y - (FLOOR_TILE_SIZE + HERO_RADIUS) * 2)
    },
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

function clampRectToWorld(rect: HudMinimapRect, worldWidth: number, worldHeight: number): HudMinimapRect {
  const x = clamp(rect.x, 0, worldWidth);
  const y = clamp(rect.y, 0, worldHeight);
  const right = clamp(rect.x + rect.width, 0, worldWidth);
  const bottom = clamp(rect.y + rect.height, 0, worldHeight);

  return {
    x,
    y,
    width: Math.max(0, right - x),
    height: Math.max(0, bottom - y)
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
