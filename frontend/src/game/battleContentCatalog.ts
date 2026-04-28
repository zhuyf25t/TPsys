import type {
  ItemPickup,
  PickupSpawnPoint,
  ProjectileKind,
  SkillKind,
  Vec2,
  WeaponKind,
  WeaponPickup
} from "../domain/types";

export interface WeaponDefinition {
  displayName: string;
  projectileKind: ProjectileKind;
  cooldownMs: number;
  reloadMs: number;
  speed: number;
  damage: number;
  lifetimeMs: number;
  radius: number;
  splashRadius: number;
  pellets: number;
  spreadRadians: number;
  magazineSize: number;
  reserveAmmo: number | null;
  pickupAmmo: number;
  usesHeat: boolean;
  maxHeat: number;
  heatPerShot: number;
  coolRatePerSecond: number;
  overheatLockMs: number;
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

export interface HeroDefinition {
  heroId: string;
  displayName: string;
  position: Vec2;
}

export interface WeaponPickupDefinition {
  weaponId: string;
  weaponKind: WeaponPickup["weaponKind"];
  position: Vec2;
}

export interface ItemPickupDefinition {
  pickupId: string;
  kind: ItemPickup["kind"];
  position: Vec2;
}

export const WEAPON_DEFINITIONS: Readonly<Record<WeaponKind, Readonly<WeaponDefinition>>> = {
  Pistol: {
    displayName: "手枪",
    projectileKind: "pistol-bullet",
    cooldownMs: 260,
    reloadMs: 1000,
    speed: 920,
    damage: 12,
    lifetimeMs: 900,
    radius: 8,
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
    overheatLockMs: 0
  },
  RocketLauncher: {
    displayName: "火箭炮",
    projectileKind: "rocket",
    cooldownMs: 160,
    reloadMs: 2500,
    speed: 340,
    damage: 60,
    lifetimeMs: 2200,
    radius: 14,
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
    overheatLockMs: 0
  },
  Gatling: {
    displayName: "加特林",
    projectileKind: "gatling-bullet",
    cooldownMs: 72,
    reloadMs: 0,
    speed: 980,
    damage: 5,
    lifetimeMs: 620,
    radius: 7,
    splashRadius: 0,
    pellets: 1,
    spreadRadians: 0.06,
    magazineSize: 0,
    reserveAmmo: null,
    pickupAmmo: 0,
    usesHeat: true,
    maxHeat: 100,
    heatPerShot: 8,
    coolRatePerSecond: 32,
    overheatLockMs: 1400
  },
  Shotgun: {
    displayName: "霰弹枪",
    projectileKind: "shotgun-pellet",
    cooldownMs: 760,
    reloadMs: 1200,
    speed: 720,
    damage: 8,
    lifetimeMs: 330,
    radius: 7,
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
    overheatLockMs: 0
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
    cooldownMs: 2600,
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

export const HERO_DEFINITIONS: ReadonlyArray<Readonly<HeroDefinition>> = [
  { heroId: "player-1", displayName: "玩家-1", position: { x: 704, y: 800 } },
  { heroId: "bot-1", displayName: "机器人-1", position: { x: 512, y: 544 } },
  { heroId: "bot-2", displayName: "机器人-2", position: { x: 512, y: 1056 } },
  { heroId: "bot-3", displayName: "机器人-3", position: { x: 1600, y: 320 } },
  { heroId: "bot-4", displayName: "机器人-4", position: { x: 1600, y: 1280 } },
  { heroId: "bot-5", displayName: "机器人-5", position: { x: 2048, y: 800 } }
];

export const HERO_SPAWN_POINTS: readonly Vec2[] = [
  { x: 704, y: 800 },
  { x: 512, y: 544 },
  { x: 512, y: 1056 },
  { x: 1600, y: 320 },
  { x: 1600, y: 1280 },
  { x: 2048, y: 800 }
];

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

export const WEAPON_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<WeaponPickupDefinition>> = [
  { weaponId: "pickup-rocket-1", weaponKind: "RocketLauncher", position: { x: 1280, y: 256 } },
  { weaponId: "pickup-gatling-1", weaponKind: "Gatling", position: { x: 704, y: 800 } },
  { weaponId: "pickup-shotgun-1", weaponKind: "Shotgun", position: { x: 1856, y: 800 } },
  { weaponId: "pickup-rocket-2", weaponKind: "RocketLauncher", position: { x: 1280, y: 1344 } },
  { weaponId: "pickup-gatling-2", weaponKind: "Gatling", position: { x: 448, y: 800 } },
  { weaponId: "pickup-shotgun-2", weaponKind: "Shotgun", position: { x: 2112, y: 800 } }
];

export const WEAPON_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = WEAPON_PICKUP_DEFINITIONS.map(
  (definition, index) => ({
    id: `weapon-pad-${index + 1}`,
    kind: "weapon",
    position: definition.position,
    occupied: false
  })
);

export const ITEM_PICKUP_DEFINITIONS: ReadonlyArray<Readonly<ItemPickupDefinition>> = [
  { pickupId: "pickup-medkit-1", kind: "Medkit", position: { x: 960, y: 608 } },
  { pickupId: "pickup-medkit-2", kind: "Medkit", position: { x: 1600, y: 992 } }
];

export const ITEM_PICKUP_SPAWN_POINTS: readonly PickupSpawnPoint[] = ITEM_PICKUP_DEFINITIONS.map(
  (definition, index) => ({
    id: `medkit-pad-${index + 1}`,
    kind: "medkit",
    position: definition.position,
    occupied: false
  })
);
