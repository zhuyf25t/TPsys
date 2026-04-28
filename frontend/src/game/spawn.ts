import type { Hero, ItemPickup, PickupSpawnPoint, Vec2, WeaponPickup } from "../domain/types";
import { getCurrentAuthHandle, getCurrentAuthSkin } from "../features/auth/authGateway";
import { HERO_MAX_HP, HERO_MAX_STAMINA, HERO_RADIUS, TEAM_MODE, WEAPON_PICKUP_RESPAWN_MS } from "./constants";
import { createDefaultSkills } from "./skills";
import { createStarterInventory } from "./weapons";

export interface HeroVisualDefinition {
  textureKey: string;
  tint: number;
}

export interface InitialHeroConfig {
  heroId: string;
  displayName?: string;
  skin?: string;
}

const HERO_DEFINITIONS: readonly {
  heroId: string;
  displayName: string;
  position: Vec2;
}[] = [
  { heroId: "player-1", displayName: "玩家-1", position: { x: 704, y: 800 } },
  { heroId: "bot-1", displayName: "机器人-1", position: { x: 512, y: 544 } },
  { heroId: "bot-2", displayName: "机器人-2", position: { x: 512, y: 1056 } },
  { heroId: "bot-3", displayName: "机器人-3", position: { x: 1600, y: 320 } },
  { heroId: "bot-4", displayName: "机器人-4", position: { x: 1600, y: 1280 } },
  { heroId: "bot-5", displayName: "机器人-5", position: { x: 2048, y: 800 } }
] as const;

export const HERO_SPAWN_POINTS: readonly Vec2[] = [
  { x: 704, y: 800 },
  { x: 512, y: 544 },
  { x: 512, y: 1056 },
  { x: 1600, y: 320 },
  { x: 1600, y: 1280 },
  { x: 2048, y: 800 }
] as const;

const HERO_VISUALS: Record<string, HeroVisualDefinition> = {
  "player-1": { textureKey: "hero-player", tint: 0x7ae2ff },
  "bot-1": { textureKey: "hero-survivor", tint: 0x7dd87d },
  "bot-2": { textureKey: "hero-soldier", tint: 0xffd36e },
  "bot-3": { textureKey: "hero-brown", tint: 0xff9d7a },
  "bot-4": { textureKey: "hero-old", tint: 0xc8b6ff },
  "bot-5": { textureKey: "hero-woman", tint: 0x87f0d6 }
};

const SKIN_VISUALS: Record<string, HeroVisualDefinition> = {
  blue: { textureKey: "hero-player", tint: 0x7ae2ff },
  survivor: { textureKey: "hero-survivor", tint: 0x7dd87d },
  soldier: { textureKey: "hero-soldier", tint: 0xffd36e },
  brown: { textureKey: "hero-brown", tint: 0xff9d7a },
  old: { textureKey: "hero-old", tint: 0xc8b6ff },
  woman: { textureKey: "hero-woman", tint: 0x87f0d6 }
};

let heroVisualOverrides = new Map<string, HeroVisualDefinition>();

const WEAPON_PICKUP_DEFINITIONS: readonly {
  weaponId: string;
  weaponKind: WeaponPickup["weaponKind"];
  position: Vec2;
}[] = [
  { weaponId: "pickup-rocket-1", weaponKind: "RocketLauncher", position: { x: 1280, y: 256 } },
  { weaponId: "pickup-gatling-1", weaponKind: "Gatling", position: { x: 704, y: 800 } },
  { weaponId: "pickup-shotgun-1", weaponKind: "Shotgun", position: { x: 1856, y: 800 } },
  { weaponId: "pickup-rocket-2", weaponKind: "RocketLauncher", position: { x: 1280, y: 1344 } },
  { weaponId: "pickup-gatling-2", weaponKind: "Gatling", position: { x: 448, y: 800 } },
  { weaponId: "pickup-shotgun-2", weaponKind: "Shotgun", position: { x: 2112, y: 800 } }
] as const;

export const WEAPON_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = WEAPON_PICKUP_DEFINITIONS.map((definition, index) => ({
  id: `weapon-pad-${index + 1}`,
  kind: "weapon",
  position: definition.position,
  occupied: false
}));

const ITEM_PICKUP_DEFINITIONS: readonly {
  pickupId: string;
  kind: ItemPickup["kind"];
  position: Vec2;
}[] = [
  { pickupId: "pickup-medkit-1", kind: "Medkit", position: { x: 960, y: 608 } },
  { pickupId: "pickup-medkit-2", kind: "Medkit", position: { x: 1600, y: 992 } }
] as const;

export const ITEM_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = ITEM_PICKUP_DEFINITIONS.map((definition, index) => ({
  id: `medkit-pad-${index + 1}`,
  kind: "medkit",
  position: definition.position,
  occupied: false
}));

export function resolveHeroVisual(heroId: string): HeroVisualDefinition {
  const override = heroVisualOverrides.get(heroId);
  if (override) {
    return override;
  }

  if (heroId === "player-1") {
    const skin = getCurrentAuthSkin();
    return {
      textureKey: skin.textureKey,
      tint: skin.tint
    };
  }

  return HERO_VISUALS[heroId];
}

export function createInitialHeroes(initialHeroes?: readonly InitialHeroConfig[]): Hero[] {
  const playerHandle = getCurrentAuthHandle();
  const heroConfigById = new Map(initialHeroes?.map((config) => [config.heroId, config]) ?? []);
  heroVisualOverrides = new Map(
    (initialHeroes ?? [])
      .map((config) => {
        const visual = resolveSkinVisual(config.skin);
        return visual ? ([config.heroId, visual] as const) : null;
      })
      .filter((entry): entry is readonly [string, HeroVisualDefinition] => entry !== null)
  );

  return HERO_DEFINITIONS.map((definition) => ({
    ...createStarterInventory(),
    heroId: definition.heroId,
    displayName:
      heroConfigById.get(definition.heroId)?.displayName ??
      (definition.heroId === "player-1" ? playerHandle : definition.displayName),
    team: TEAM_MODE,
    hp: HERO_MAX_HP,
    maxHp: HERO_MAX_HP,
    stamina: HERO_MAX_STAMINA,
    maxStamina: HERO_MAX_STAMINA,
    position: { x: definition.position.x, y: definition.position.y },
    facing: 0,
    radius: HERO_RADIUS,
    alive: true,
    lifeState: "alive",
    score: 0,
    skills: createDefaultSkills(),
    preparedSkill: null,
    velocity: { x: 0, y: 0 },
    respawnMs: 0,
    jumpCooldownMs: 0,
    eliminatedAtMs: null
  }));
}

function resolveSkinVisual(skin: string | undefined): HeroVisualDefinition | null {
  if (!skin) {
    return null;
  }

  return SKIN_VISUALS[skin] ?? null;
}

export function createInitialWeaponPickups(): WeaponPickup[] {
  return WEAPON_PICKUP_DEFINITIONS.map((definition) => ({
    weaponId: definition.weaponId,
    weaponKind: definition.weaponKind,
    position: { x: definition.position.x, y: definition.position.y },
    available: true,
    respawnMs: 0
  }));
}

export function createInitialItemPickups(): ItemPickup[] {
  return ITEM_PICKUP_DEFINITIONS.map((definition) => ({
    pickupId: definition.pickupId,
    kind: definition.kind,
    position: { x: definition.position.x, y: definition.position.y },
    available: true,
    respawnMs: 0
  }));
}

export function chooseRandomSpawnPoint(randomValue: number): Vec2 {
  const index = Math.floor(randomValue * HERO_SPAWN_POINTS.length) % HERO_SPAWN_POINTS.length;
  const point = HERO_SPAWN_POINTS[index];

  return {
    x: point.x,
    y: point.y
  };
}

export function chooseRandomPickupSpawnPoint(randomValue: number): Vec2 {
  const index = Math.floor(randomValue * WEAPON_PICKUP_SPAWN_POINTS.length) % WEAPON_PICKUP_SPAWN_POINTS.length;
  const point = WEAPON_PICKUP_SPAWN_POINTS[index]?.position ?? WEAPON_PICKUP_DEFINITIONS[0].position;

  return {
    x: point.x,
    y: point.y
  };
}

export function chooseRandomItemPickupSpawnPoint(randomValue: number): Vec2 {
  const index = Math.floor(randomValue * ITEM_PICKUP_SPAWN_POINTS.length) % ITEM_PICKUP_SPAWN_POINTS.length;
  const point = ITEM_PICKUP_SPAWN_POINTS[index]?.position ?? ITEM_PICKUP_DEFINITIONS[0].position;

  return {
    x: point.x,
    y: point.y
  };
}

export function resetPickupRespawn(pickup: WeaponPickup): WeaponPickup {
  return {
    ...pickup,
    available: false,
    respawnMs: WEAPON_PICKUP_RESPAWN_MS
  };
}
