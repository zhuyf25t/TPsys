import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { getCurrentAuthHandle, getCurrentAuthSkin } from "../../../../apis/identity/authGateway";
import {
  HERO_DEFINITIONS,
  HERO_SPAWN_POINTS,
  ITEM_PICKUP_DEFINITIONS,
  ITEM_PICKUP_SPAWN_POINTS,
  WEAPON_PICKUP_DEFINITIONS,
  WEAPON_PICKUP_SPAWN_POINTS,
  getHeroDefinitions,
  getHeroSpawnPoints,
  getItemPickupDefinitions,
  getItemPickupSpawnPoints,
  getWeaponPickupDefinitions,
  getWeaponPickupSpawnPoints
} from "../../microservices/world/functions/BattleWorldInitialLayout";
import { HERO_VISUALS, SKIN_VISUALS, type HeroVisualDefinition } from "../objects/BattleHeroVisualCatalog";
import { HERO_MAX_HP, HERO_MAX_STAMINA, HERO_RADIUS, TEAM_MODE, WEAPON_PICKUP_RESPAWN_MS } from "../objects/BattleGameConstants";
import { createDefaultSkills } from "../../microservices/abilities/functions/BattleSkillStateRules";
import { createStarterInventory } from "../../microservices/combat/functions/BattleWeaponInventoryRules";

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

/** 中文名：解析英雄visual（resolveHeroVisual）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
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

/** 中文名：创建initialheroes（createInitialHeroes）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
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
    .slice(0, getHeroDefinitions().length);

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

  return getHeroDefinitions().map((definition) => ({
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
  const heroDefinitions = getHeroDefinitions();
  const index =
    typeof spawnPointIndex === "number" && Number.isFinite(spawnPointIndex)
      ? Math.max(0, Math.trunc(spawnPointIndex))
      : fallbackIndex;
  return heroDefinitions[index] ?? heroDefinitions[fallbackIndex] ?? heroDefinitions[0] ?? HERO_DEFINITIONS[0];
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

/** 中文名：创建initial武器pickups（createInitialWeaponPickups）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function createInitialWeaponPickups(): WeaponPickup[] {
  return getWeaponPickupDefinitions().map((definition) => ({
    pickupId: definition.pickupId,
    weaponKind: definition.weaponKind,
    position: { x: definition.position.x, y: definition.position.y },
    available: true,
    respawnMs: 0
  }));
}

/** 中文名：创建initialitempickups（createInitialItemPickups）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function createInitialItemPickups(): ItemPickup[] {
  return getItemPickupDefinitions().map((definition) => ({
    pickupId: definition.pickupId,
    kind: definition.kind,
    position: { x: definition.position.x, y: definition.position.y },
    available: true,
    respawnMs: 0
  }));
}

/** 中文名：选择randomspawnpoint（chooseRandomSpawnPoint）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function chooseRandomSpawnPoint(randomValue: number): Vec2 {
  const spawnPoints = getHeroSpawnPoints();
  const index = Math.floor(randomValue * spawnPoints.length) % spawnPoints.length;
  const point = spawnPoints[index] ?? HERO_SPAWN_POINTS[0];

  return {
    x: point.x,
    y: point.y
  };
}

/** 中文名：选择random拾取物spawnpoint（chooseRandomPickupSpawnPoint）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function chooseRandomPickupSpawnPoint(randomValue: number): Vec2 {
  const spawnPoints = getWeaponPickupSpawnPoints();
  const definitions = getWeaponPickupDefinitions();
  const index = Math.floor(randomValue * spawnPoints.length) % spawnPoints.length;
  const point = spawnPoints[index]?.position ?? definitions[0]?.position ?? WEAPON_PICKUP_DEFINITIONS[0].position;

  return {
    x: point.x,
    y: point.y
  };
}

/** 中文名：选择randomitem拾取物spawnpoint（chooseRandomItemPickupSpawnPoint）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function chooseRandomItemPickupSpawnPoint(randomValue: number): Vec2 {
  const spawnPoints = getItemPickupSpawnPoints();
  const definitions = getItemPickupDefinitions();
  const index = Math.floor(randomValue * spawnPoints.length) % spawnPoints.length;
  const point = spawnPoints[index]?.position ?? definitions[0]?.position ?? ITEM_PICKUP_DEFINITIONS[0].position;

  return {
    x: point.x,
    y: point.y
  };
}

/** 中文名：重置拾取物respawn（resetPickupRespawn）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致�?*/
export function resetPickupRespawn(pickup: WeaponPickup): WeaponPickup {
  return {
    ...pickup,
    available: false,
    respawnMs: WEAPON_PICKUP_RESPAWN_MS
  };
}
