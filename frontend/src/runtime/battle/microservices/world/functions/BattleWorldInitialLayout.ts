import type { PickupSpawnPoint } from "../../../../../objects/battle/microservices/world/objects/world/PickupSpawnPoint";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  DEFAULT_BATTLE_MAP,
  getActiveBattleMap,
  type HeroDefinition,
  type ItemPickupDefinition,
  type WeaponPickupDefinition
} from "../services/BattleArenaCatalog";

export type { HeroDefinition, ItemPickupDefinition, WeaponPickupDefinition } from "../services/BattleArenaCatalog";

export const HERO_DEFINITIONS: ReadonlyArray<Readonly<HeroDefinition>> = DEFAULT_BATTLE_MAP.heroDefinitions;

export const HERO_SPAWN_POINTS: readonly Vec2[] = DEFAULT_BATTLE_MAP.heroSpawnPoints;

export function getHeroDefinitions(): ReadonlyArray<Readonly<HeroDefinition>> {
  return getActiveBattleMap().heroDefinitions;
}

export function getHeroSpawnPoints(): readonly Vec2[] {
  return getActiveBattleMap().heroSpawnPoints;
}

export const WEAPON_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<WeaponPickupDefinition>> =
  DEFAULT_BATTLE_MAP.weaponPickupDefinitions;

export const WEAPON_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = DEFAULT_BATTLE_MAP.weaponPickupSpawnPoints;

export const ITEM_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<ItemPickupDefinition>> =
  DEFAULT_BATTLE_MAP.itemPickupDefinitions;

export const ITEM_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = DEFAULT_BATTLE_MAP.itemPickupSpawnPoints;

export function getWeaponPickupDefinitions(): ReadonlyArray<Readonly<WeaponPickupDefinition>> {
  return getActiveBattleMap().weaponPickupDefinitions;
}

export function getWeaponPickupSpawnPoints(): readonly PickupSpawnPoint[] {
  return getActiveBattleMap().weaponPickupSpawnPoints;
}

export function getItemPickupDefinitions(): ReadonlyArray<Readonly<ItemPickupDefinition>> {
  return getActiveBattleMap().itemPickupDefinitions;
}

export function getItemPickupSpawnPoints(): readonly PickupSpawnPoint[] {
  return getActiveBattleMap().itemPickupSpawnPoints;
}
