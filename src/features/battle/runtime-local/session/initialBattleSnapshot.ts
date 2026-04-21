import type { GameSnapshot } from "../../../../domain/types";
import { WORLD_SIZE } from "../../../../game/constants";
import { createInitialHeroes, createInitialItemPickups, createInitialWeaponPickups } from "../../../../game/spawn";

export function createInitialBattleSnapshot(): GameSnapshot {
  return {
    heroes: createInitialHeroes(),
    projectiles: [],
    slowFields: [],
    weaponPickups: createInitialWeaponPickups(),
    itemPickups: createInitialItemPickups(),
    events: [],
    worldSize: { x: WORLD_SIZE.x, y: WORLD_SIZE.y },
    elapsedMs: 0,
    playerHeroId: "player-1"
  };
}
