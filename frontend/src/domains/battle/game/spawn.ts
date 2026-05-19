import type { Hero, ItemPickup, Vec2, WeaponPickup } from "../objects/types";
import { getCurrentAuthHandle, getCurrentAuthSkin } from "../../identity/api/authGateway";
import {
  HERO_DEFINITIONS,
  HERO_SPAWN_POINTS,
  HERO_VISUALS,
  ITEM_PICKUP_DEFINITIONS,
  ITEM_PICKUP_SPAWN_POINTS,
  SKIN_VISUALS,
  WEAPON_PICKUP_DEFINITIONS,
  WEAPON_PICKUP_SPAWN_POINTS,
  type HeroVisualDefinition
} from "./assets/battleContentCatalog";
import { HERO_MAX_HP, HERO_MAX_STAMINA, HERO_RADIUS, TEAM_MODE, WEAPON_PICKUP_RESPAWN_MS } from "./constants";
import { createDefaultSkills } from "./skills";
import { createStarterInventory } from "./weapons";

export {
  HERO_SPAWN_POINTS,
  ITEM_PICKUP_SPAWN_POINTS,
  WEAPON_PICKUP_SPAWN_POINTS,
  type HeroVisualDefinition
};

export interface InitialHeroConfig {
  heroId: string;
  displayName?: string;
  skin?: string;
  spawnPointIndex?: number;
}

let heroVisualOverrides = new Map<string, HeroVisualDefinition>();

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

  return HERO_VISUALS[heroId] ?? HERO_VISUALS["player-1"];
}

export function createInitialHeroes(initialHeroes?: readonly InitialHeroConfig[]): Hero[] {
  const playerHandle = getCurrentAuthHandle();
  const heroDefinitions = resolveInitialHeroDefinitions(initialHeroes, playerHandle);
  heroVisualOverrides = hasProvidedHeroDefinitions(initialHeroes)
    ? buildHeroVisualOverrides(heroDefinitions)
    : new Map();

  return heroDefinitions.map((definition) => ({
    ...createStarterInventory(),
    heroId: definition.heroId,
    displayName: definition.displayName,
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

function hasProvidedHeroDefinitions(initialHeroes: readonly InitialHeroConfig[] | undefined): boolean {
  return Boolean(initialHeroes?.some((config) => config.heroId.trim().length > 0));
}

interface ResolvedInitialHeroDefinition {
  heroId: string;
  displayName: string;
  skin?: string;
  position: Vec2;
  visual: HeroVisualDefinition;
}

function resolveInitialHeroDefinitions(
  initialHeroes: readonly InitialHeroConfig[] | undefined,
  playerHandle: string
): ResolvedInitialHeroDefinition[] {
  const providedHeroes = (initialHeroes ?? [])
    .map((config) => ({
      ...config,
      heroId: config.heroId.trim()
    }))
    .filter((config) => config.heroId.length > 0)
    .slice(0, HERO_DEFINITIONS.length);

  if (providedHeroes.length > 0) {
    return providedHeroes.map((config, index) => {
      const fallbackDefinition = resolveSpawnDefinition(config.spawnPointIndex, index);
      const fallbackVisual = resolveDefinitionVisual(fallbackDefinition.heroId);

      return {
        heroId: config.heroId,
        displayName: normalizeOptionalText(config.displayName) ?? fallbackDefinition.displayName,
        ...(config.skin ? { skin: config.skin } : {}),
        position: { x: fallbackDefinition.position.x, y: fallbackDefinition.position.y },
        visual: resolveSkinVisual(config.skin) ?? fallbackVisual
      };
    });
  }

  return HERO_DEFINITIONS.map((definition) => ({
    heroId: definition.heroId,
    displayName: definition.heroId === "player-1" ? playerHandle : definition.displayName,
    position: { x: definition.position.x, y: definition.position.y },
    visual: resolveDefinitionVisual(definition.heroId)
  }));
}

function buildHeroVisualOverrides(
  heroDefinitions: readonly ResolvedInitialHeroDefinition[]
): Map<string, HeroVisualDefinition> {
  return new Map(heroDefinitions.map((definition) => [definition.heroId, definition.visual] as const));
}

function resolveSpawnDefinition(spawnPointIndex: number | undefined, fallbackIndex: number): (typeof HERO_DEFINITIONS)[number] {
  const index =
    typeof spawnPointIndex === "number" && Number.isFinite(spawnPointIndex)
      ? Math.max(0, Math.trunc(spawnPointIndex))
      : fallbackIndex;
  return HERO_DEFINITIONS[index] ?? HERO_DEFINITIONS[fallbackIndex] ?? HERO_DEFINITIONS[0];
}

function resolveDefinitionVisual(heroId: string): HeroVisualDefinition {
  return HERO_VISUALS[heroId] ?? HERO_VISUALS["player-1"];
}

function normalizeOptionalText(value: string | undefined): string | null {
  const normalized = value?.trim() ?? "";
  return normalized ? normalized : null;
}

function resolveSkinVisual(skin: string | undefined): HeroVisualDefinition | null {
  if (!skin) {
    return null;
  }

  return SKIN_VISUALS[skin] ?? null;
}

export function createInitialWeaponPickups(): WeaponPickup[] {
  return WEAPON_PICKUP_DEFINITIONS.map((definition) => ({
    weaponId: definition.pickupId,
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
