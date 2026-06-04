import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { FLOOR_TILE_SIZE, HERO_RADIUS } from "../objects/BattleGameConstants";
import type { HudMinimapData, HudMinimapRect } from "../ui/Hud";

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

/** 涓枃鍚嶏細鍒涘缓minimap鏁版嵁锛坈reateMinimapData锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷淬€?*/
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
    gasZone: snapshot.gasZone
      ? {
          x: snapshot.gasZone.center.x,
          y: snapshot.gasZone.center.y,
          radius: snapshot.gasZone.radius,
          nextRadius: snapshot.gasZone.nextRadius,
          phase: snapshot.gasZone.phase
        }
      : null,
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
