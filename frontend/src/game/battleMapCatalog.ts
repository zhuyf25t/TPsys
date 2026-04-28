import type { ItemPickup, PickupSpawnPoint, Vec2, WeaponPickup } from "../domain/types";

export interface ArenaObstacle {
  obstacleId: string;
  kind: "wall" | "crate";
  position: Vec2;
  size: Vec2;
}

export interface HeroDefinition {
  heroId: string;
  displayName: string;
  position: Vec2;
}

export interface WeaponPickupDefinition {
  pickupId: string;
  weaponKind: WeaponPickup["weaponKind"];
  position: Vec2;
}

export interface ItemPickupDefinition {
  pickupId: string;
  kind: ItemPickup["kind"];
  position: Vec2;
}

export interface BattleMapConfig {
  mapId: string;
  displayName: string;
  themeId: string;
  worldSize: Vec2;
  heroDefinitions: ReadonlyArray<Readonly<HeroDefinition>>;
  heroSpawnPoints: readonly Vec2[];
  innerObstacles: readonly ArenaObstacle[];
  weaponPickupDefinitions: ReadonlyArray<Readonly<WeaponPickupDefinition>>;
  itemPickupDefinitions: ReadonlyArray<Readonly<ItemPickupDefinition>>;
  weaponPickupSpawnPoints: readonly PickupSpawnPoint[];
  itemPickupSpawnPoints: readonly PickupSpawnPoint[];
}

const HERO_DEFINITIONS = [
  { heroId: "player-1", displayName: "玩家-1", position: { x: 704, y: 800 } },
  { heroId: "bot-1", displayName: "机器人-1", position: { x: 512, y: 544 } },
  { heroId: "bot-2", displayName: "机器人-2", position: { x: 512, y: 1056 } },
  { heroId: "bot-3", displayName: "机器人-3", position: { x: 1600, y: 320 } },
  { heroId: "bot-4", displayName: "机器人-4", position: { x: 1600, y: 1280 } },
  { heroId: "bot-5", displayName: "机器人-5", position: { x: 2048, y: 800 } }
] as const satisfies ReadonlyArray<Readonly<HeroDefinition>>;

const HERO_SPAWN_POINTS = [
  { x: 704, y: 800 },
  { x: 512, y: 544 },
  { x: 512, y: 1056 },
  { x: 1600, y: 320 },
  { x: 1600, y: 1280 },
  { x: 2048, y: 800 }
] as const satisfies readonly Vec2[];

const INNER_OBSTACLES = [
  { obstacleId: "cover-nw-1", kind: "wall", position: { x: 416, y: 416 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-nw-2", kind: "wall", position: { x: 480, y: 416 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-nw-3", kind: "wall", position: { x: 416, y: 480 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-ne-1", kind: "wall", position: { x: 2144, y: 416 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-ne-2", kind: "wall", position: { x: 2080, y: 416 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-ne-3", kind: "wall", position: { x: 2144, y: 480 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-sw-1", kind: "wall", position: { x: 416, y: 1184 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-sw-2", kind: "wall", position: { x: 480, y: 1184 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-sw-3", kind: "wall", position: { x: 416, y: 1120 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-se-1", kind: "wall", position: { x: 2144, y: 1184 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-se-2", kind: "wall", position: { x: 2080, y: 1184 }, size: { x: 64, y: 64 } },
  { obstacleId: "cover-se-3", kind: "wall", position: { x: 2144, y: 1120 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-top-1", kind: "wall", position: { x: 1184, y: 448 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-top-2", kind: "wall", position: { x: 1248, y: 448 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-top-3", kind: "wall", position: { x: 1312, y: 448 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-top-4", kind: "wall", position: { x: 1376, y: 448 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-bot-1", kind: "wall", position: { x: 1184, y: 1152 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-bot-2", kind: "wall", position: { x: 1248, y: 1152 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-bot-3", kind: "wall", position: { x: 1312, y: 1152 }, size: { x: 64, y: 64 } },
  { obstacleId: "center-bot-4", kind: "wall", position: { x: 1376, y: 1152 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-left-1", kind: "wall", position: { x: 928, y: 640 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-left-2", kind: "wall", position: { x: 928, y: 704 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-left-3", kind: "wall", position: { x: 928, y: 896 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-left-4", kind: "wall", position: { x: 928, y: 960 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-right-1", kind: "wall", position: { x: 1632, y: 640 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-right-2", kind: "wall", position: { x: 1632, y: 704 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-right-3", kind: "wall", position: { x: 1632, y: 896 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-right-4", kind: "wall", position: { x: 1632, y: 960 }, size: { x: 64, y: 64 } },
  { obstacleId: "crate-mid-top-left", kind: "crate", position: { x: 1184, y: 736 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-mid-top-right", kind: "crate", position: { x: 1376, y: 736 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-mid-bottom-left", kind: "crate", position: { x: 1184, y: 864 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-mid-bottom-right", kind: "crate", position: { x: 1376, y: 864 }, size: { x: 48, y: 48 } },
  { obstacleId: "mid-west-1", kind: "wall", position: { x: 640, y: 704 }, size: { x: 64, y: 64 } },
  { obstacleId: "mid-west-2", kind: "wall", position: { x: 640, y: 896 }, size: { x: 64, y: 64 } },
  { obstacleId: "mid-east-1", kind: "wall", position: { x: 1920, y: 704 }, size: { x: 64, y: 64 } },
  { obstacleId: "mid-east-2", kind: "wall", position: { x: 1920, y: 896 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-top-left", kind: "wall", position: { x: 1056, y: 608 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-top-right", kind: "wall", position: { x: 1504, y: 608 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-bottom-left", kind: "wall", position: { x: 1056, y: 992 }, size: { x: 64, y: 64 } },
  { obstacleId: "lane-bottom-right", kind: "wall", position: { x: 1504, y: 992 }, size: { x: 64, y: 64 } },
  { obstacleId: "crate-west-top", kind: "crate", position: { x: 768, y: 640 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-west-bottom", kind: "crate", position: { x: 768, y: 960 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-east-top", kind: "crate", position: { x: 1792, y: 640 }, size: { x: 48, y: 48 } },
  { obstacleId: "crate-east-bottom", kind: "crate", position: { x: 1792, y: 960 }, size: { x: 48, y: 48 } }
] as const satisfies readonly ArenaObstacle[];

const WEAPON_PICKUP_DEFINITIONS = [
  { pickupId: "pickup-rocket-1", weaponKind: "RocketLauncher", position: { x: 1280, y: 256 } },
  { pickupId: "pickup-gatling-1", weaponKind: "Gatling", position: { x: 704, y: 800 } },
  { pickupId: "pickup-shotgun-1", weaponKind: "Shotgun", position: { x: 1856, y: 800 } },
  { pickupId: "pickup-rocket-2", weaponKind: "RocketLauncher", position: { x: 1280, y: 1344 } },
  { pickupId: "pickup-gatling-2", weaponKind: "Gatling", position: { x: 448, y: 800 } },
  { pickupId: "pickup-shotgun-2", weaponKind: "Shotgun", position: { x: 2112, y: 800 } }
] as const satisfies ReadonlyArray<Readonly<WeaponPickupDefinition>>;

const ITEM_PICKUP_DEFINITIONS = [
  { pickupId: "pickup-medkit-1", kind: "Medkit", position: { x: 960, y: 608 } },
  { pickupId: "pickup-medkit-2", kind: "Medkit", position: { x: 1600, y: 992 } }
] as const satisfies ReadonlyArray<Readonly<ItemPickupDefinition>>;

const WEAPON_PICKUP_SPAWN_POINTS = WEAPON_PICKUP_DEFINITIONS.map(
  (definition, index): PickupSpawnPoint => ({
    id: `weapon-pad-${index + 1}`,
    kind: "weapon",
    position: definition.position,
    occupied: false
  })
);

const ITEM_PICKUP_SPAWN_POINTS = ITEM_PICKUP_DEFINITIONS.map(
  (definition, index): PickupSpawnPoint => ({
    id: `medkit-pad-${index + 1}`,
    kind: "medkit",
    position: definition.position,
    occupied: false
  })
);

export const DEFAULT_BATTLE_MAP = {
  mapId: "default-industrial-arena",
  displayName: "默认工业竞技场",
  themeId: "industrial",
  worldSize: { x: 2560, y: 1600 },
  heroDefinitions: HERO_DEFINITIONS,
  heroSpawnPoints: HERO_SPAWN_POINTS,
  innerObstacles: INNER_OBSTACLES,
  weaponPickupDefinitions: WEAPON_PICKUP_DEFINITIONS,
  itemPickupDefinitions: ITEM_PICKUP_DEFINITIONS,
  weaponPickupSpawnPoints: WEAPON_PICKUP_SPAWN_POINTS,
  itemPickupSpawnPoints: ITEM_PICKUP_SPAWN_POINTS
} as const satisfies BattleMapConfig;

export const BATTLE_MAPS = {
  [DEFAULT_BATTLE_MAP.mapId]: DEFAULT_BATTLE_MAP
} as const satisfies Readonly<Record<string, BattleMapConfig>>;
