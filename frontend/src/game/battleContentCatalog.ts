import type { PickupSpawnPoint, ProjectileKind, SkillKind, Vec2, WeaponKind } from "../domain/types";
import {
  DEFAULT_BATTLE_MAP,
  type HeroDefinition,
  type ItemPickupDefinition,
  type WeaponPickupDefinition
} from "./battleMapCatalog";

export type { HeroDefinition, ItemPickupDefinition, WeaponPickupDefinition } from "./battleMapCatalog";

export interface WeaponDefinition {
  displayName: string;
  projectileKind: ProjectileKind;
  cooldownMs: number;
  reloadMs: number;
  projectileSpeedPerSecond: number;
  projectileDamage: number;
  projectileLifetimeMs: number;
  projectileRadius: number;
  splashRadius: number;
  pellets: number;
  spreadRadians: number;
  magazineSize: number;
  reserveAmmo: number;
  pickupAmmo: number;
  usesHeat: boolean;
  maxHeat: number;
  heatPerShot: number;
  coolRatePerSecond: number;
  overheatLockMs: number;
  recoilStrength: number;
}

export interface SkillDefinition {
  cooldownMs: number;
  range: number;
  radius: number;
  durationMs: number;
  healAmount: number;
  distance: number;
}

export interface HeroVisualDefinition {
  textureKey: string;
  tint: number;
}

export const WEAPON_DEFINITIONS: Readonly<Record<WeaponKind, Readonly<WeaponDefinition>>> = {
  Pistol: {
    displayName: "手枪",
    projectileKind: "pistol-bullet",
    cooldownMs: 260,
    reloadMs: 1000,
    projectileSpeedPerSecond: 920,
    projectileDamage: 12,
    projectileLifetimeMs: 900,
    projectileRadius: 8,
    splashRadius: 0,
    pellets: 1,
    spreadRadians: 0,
    magazineSize: 12,
    reserveAmmo: 48,
    pickupAmmo: 24,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 20
  },
  RocketLauncher: {
    displayName: "火箭炮",
    projectileKind: "rocket",
    cooldownMs: 160,
    reloadMs: 2500,
    projectileSpeedPerSecond: 340,
    projectileDamage: 60,
    projectileLifetimeMs: 2200,
    projectileRadius: 14,
    splashRadius: 132,
    pellets: 1,
    spreadRadians: 0,
    magazineSize: 1,
    reserveAmmo: 3,
    pickupAmmo: 1,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 120
  },
  Gatling: {
    displayName: "加特林",
    projectileKind: "gatling-bullet",
    cooldownMs: 72,
    reloadMs: 0,
    projectileSpeedPerSecond: 980,
    projectileDamage: 5,
    projectileLifetimeMs: 620,
    projectileRadius: 7,
    splashRadius: 0,
    pellets: 1,
    spreadRadians: 0.06,
    magazineSize: 0,
    reserveAmmo: 0,
    pickupAmmo: 0,
    usesHeat: true,
    maxHeat: 100,
    heatPerShot: 8,
    coolRatePerSecond: 32,
    overheatLockMs: 1400,
    recoilStrength: 8
  },
  Shotgun: {
    displayName: "霰弹枪",
    projectileKind: "shotgun-pellet",
    cooldownMs: 760,
    reloadMs: 1200,
    projectileSpeedPerSecond: 720,
    projectileDamage: 8,
    projectileLifetimeMs: 330,
    projectileRadius: 7,
    splashRadius: 0,
    pellets: 5,
    spreadRadians: 0.42,
    magazineSize: 6,
    reserveAmmo: 18,
    pickupAmmo: 6,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 80
  }
};

export const SKILL_DEFINITIONS: Readonly<Record<SkillKind, Readonly<SkillDefinition>>> = {
  Blink: {
    cooldownMs: 2200,
    range: 250,
    radius: 0,
    durationMs: 0,
    healAmount: 0,
    distance: 0
  },
  Dash: {
    cooldownMs: 5000,
    range: 0,
    radius: 0,
    durationMs: 0,
    healAmount: 0,
    distance: 180
  },
  Freeze: {
    cooldownMs: 12000,
    range: 520,
    radius: 150,
    durationMs: 10000,
    healAmount: 0,
    distance: 0
  }
};

export const HERO_DEFINITIONS: ReadonlyArray<Readonly<HeroDefinition>> = DEFAULT_BATTLE_MAP.heroDefinitions;

export const HERO_SPAWN_POINTS: readonly Vec2[] = DEFAULT_BATTLE_MAP.heroSpawnPoints;

export const HERO_VISUALS: Readonly<Record<string, Readonly<HeroVisualDefinition>>> = {
  "player-1": { textureKey: "hero-player", tint: 0x7ae2ff },
  "bot-1": { textureKey: "hero-survivor", tint: 0x7dd87d },
  "bot-2": { textureKey: "hero-soldier", tint: 0xffd36e },
  "bot-3": { textureKey: "hero-brown", tint: 0xff9d7a },
  "bot-4": { textureKey: "hero-old", tint: 0xc8b6ff },
  "bot-5": { textureKey: "hero-woman", tint: 0x87f0d6 }
};

export const SKIN_VISUALS: Readonly<Record<string, Readonly<HeroVisualDefinition>>> = {
  blue: { textureKey: "hero-player", tint: 0x7ae2ff },
  survivor: { textureKey: "hero-survivor", tint: 0x7dd87d },
  soldier: { textureKey: "hero-soldier", tint: 0xffd36e },
  brown: { textureKey: "hero-brown", tint: 0xff9d7a },
  old: { textureKey: "hero-old", tint: 0xc8b6ff },
  woman: { textureKey: "hero-woman", tint: 0x87f0d6 }
};

export const WEAPON_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<WeaponPickupDefinition>> =
  DEFAULT_BATTLE_MAP.weaponPickupDefinitions;

export const WEAPON_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = DEFAULT_BATTLE_MAP.weaponPickupSpawnPoints;

export const ITEM_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<ItemPickupDefinition>> =
  DEFAULT_BATTLE_MAP.itemPickupDefinitions;

export const ITEM_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = DEFAULT_BATTLE_MAP.itemPickupSpawnPoints;
