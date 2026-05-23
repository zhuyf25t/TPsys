import fallHuntMapSpecJson from "../../../../../../shared/battle/maps/fall-hunt-v1.json";
import type { ItemPickup, PickupSpawnPoint, Vec2, WeaponPickup } from "../../objects/types";

export type ArenaObstacleKind = "wall" | "crate" | "tree-trunk" | "building-wall" | "rock" | "logs" | "hay" | "stump";

export type CollisionShape =
  | { kind: "aabb"; size: Vec2 }
  | { kind: "circle"; radius: number };

export type CollisionShapeSpec =
  | { kind: "aabb"; position?: Vec2; size: Vec2 }
  | { kind: "circle"; position?: Vec2; radius: number };

export interface ArenaObstacle {
  obstacleId: string;
  kind: ArenaObstacleKind;
  position: Vec2;
  size: Vec2;
  shape: CollisionShape;
  texture?: string;
  displaySize?: Vec2;
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

export interface SourceCropDefinition {
  sourceMapId: string;
  sourceSize: Vec2;
  crop: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  scale: number;
  offset: Vec2;
}

export interface TerrainPatchDefinition {
  id: string;
  kind: "grass" | "clearing" | "trail" | "water" | "mud";
  shape: "rect" | "ellipse";
  position: Vec2;
  size: Vec2;
  rotation?: number;
  color: string;
  alpha: number;
}

export interface MapTreeDefinition {
  treeId: string;
  treeKind: "big_oak_tree" | "maple_tree" | "birch_tree" | "oak_tree";
  position: Vec2;
  trunkTexture: string;
  leavesTexture: string;
  trunkSize: Vec2;
  leavesSize: Vec2;
  leavesOffset: Vec2;
  trunkCollision: CollisionShapeSpec;
  leafOcclusion: CollisionShapeSpec;
}

export interface MapObstacleDefinition {
  obstacleId: string;
  kind: "rock" | "logs" | "hay" | "stump" | "bush" | "leaf_pile";
  texture: string;
  position: Vec2;
  displaySize: Vec2;
  collision: CollisionShapeSpec | null;
}

export interface MapBuildingWallDefinition {
  wallId: string;
  collision: CollisionShapeSpec;
}

export interface MapBuildingDoorDefinition {
  doorId: string;
  position: Vec2;
  size: Vec2;
}

export interface MapBuildingDefinition {
  buildingId: string;
  kind: "barn" | "tent" | "hay_shed";
  position: Vec2;
  floorTexture: string;
  roofTexture: string;
  floorSize: Vec2;
  roofSize: Vec2;
  roofOffset: Vec2;
  interior: CollisionShapeSpec;
  walls: readonly MapBuildingWallDefinition[];
  doors: readonly MapBuildingDoorDefinition[];
}

export interface BattleMapConfig {
  mapId: string;
  displayName: string;
  themeId: string;
  worldSize: Vec2;
  sourceCrop: SourceCropDefinition;
  heroDefinitions: ReadonlyArray<Readonly<HeroDefinition>>;
  heroSpawnPoints: readonly Vec2[];
  innerObstacles: readonly ArenaObstacle[];
  terrainPatches: readonly TerrainPatchDefinition[];
  trees: readonly MapTreeDefinition[];
  decorativeObstacles: readonly MapObstacleDefinition[];
  buildings: readonly MapBuildingDefinition[];
  weaponPickupDefinitions: ReadonlyArray<Readonly<WeaponPickupDefinition>>;
  itemPickupDefinitions: ReadonlyArray<Readonly<ItemPickupDefinition>>;
  weaponPickupSpawnPoints: readonly PickupSpawnPoint[];
  itemPickupSpawnPoints: readonly PickupSpawnPoint[];
}

interface FallMapSpecJson {
  mapId: string;
  displayName: string;
  themeId: string;
  worldSize: Vec2;
  sourceCrop: SourceCropDefinition;
  heroDefinitions: HeroDefinition[];
  terrainPatches: TerrainPatchDefinition[];
  trees: MapTreeDefinition[];
  obstacles: MapObstacleDefinition[];
  buildings: MapBuildingDefinition[];
  weaponPickups: WeaponPickupDefinition[];
  itemPickups: ItemPickupDefinition[];
}

const FALL_HUNT_MAP_SPEC = fallHuntMapSpecJson as FallMapSpecJson;

function shapeAt(collision: CollisionShapeSpec): CollisionShape {
  if (collision.kind === "circle") {
    return { kind: "circle", radius: collision.radius };
  }

  return { kind: "aabb", size: copyVec(collision.size) };
}

function shapeSize(shape: CollisionShape): Vec2 {
  if (shape.kind === "circle") {
    return { x: shape.radius * 2, y: shape.radius * 2 };
  }

  return copyVec(shape.size);
}

function collisionPosition(fallback: Vec2, collision: CollisionShapeSpec): Vec2 {
  return copyVec(collision.position ?? fallback);
}

function treeObstacle(tree: MapTreeDefinition): ArenaObstacle {
  const shape = shapeAt(tree.trunkCollision);
  return {
    obstacleId: `${tree.treeId}-trunk`,
    kind: "tree-trunk",
    position: collisionPosition(tree.position, tree.trunkCollision),
    size: shapeSize(shape),
    shape,
    texture: tree.trunkTexture,
    displaySize: copyVec(tree.trunkSize)
  };
}

function mapObstacle(definition: MapObstacleDefinition): ArenaObstacle | null {
  if (!definition.collision) {
    return null;
  }

  const shape = shapeAt(definition.collision);
  return {
    obstacleId: definition.obstacleId,
    kind: obstacleKind(definition.kind),
    position: collisionPosition(definition.position, definition.collision),
    size: shapeSize(shape),
    shape,
    texture: definition.texture,
    displaySize: copyVec(definition.displaySize)
  };
}

function buildingWallObstacle(building: MapBuildingDefinition, wall: MapBuildingWallDefinition): ArenaObstacle {
  const shape = shapeAt(wall.collision);
  return {
    obstacleId: `${building.buildingId}-${wall.wallId}`,
    kind: "building-wall",
    position: collisionPosition(building.position, wall.collision),
    size: shapeSize(shape),
    shape
  };
}

function obstacleKind(kind: MapObstacleDefinition["kind"]): ArenaObstacleKind {
  switch (kind) {
    case "rock":
      return "rock";
    case "logs":
      return "logs";
    case "hay":
      return "hay";
    case "stump":
      return "stump";
    case "bush":
    case "leaf_pile":
      return "crate";
  }
}

function buildInnerObstacles(spec: FallMapSpecJson): readonly ArenaObstacle[] {
  return [
    ...spec.obstacles.map(mapObstacle).filter((obstacle): obstacle is ArenaObstacle => obstacle !== null),
    ...spec.trees.map(treeObstacle),
    ...spec.buildings.flatMap((building) => building.walls.map((wall) => buildingWallObstacle(building, wall)))
  ];
}

function pickupSpawnPoint(definition: WeaponPickupDefinition, index: number): PickupSpawnPoint {
  return {
    id: `weapon-pad-${index + 1}`,
    kind: "weapon",
    position: copyVec(definition.position),
    occupied: false
  };
}

function itemSpawnPoint(definition: ItemPickupDefinition, index: number): PickupSpawnPoint {
  return {
    id: `medkit-pad-${index + 1}`,
    kind: "medkit",
    position: copyVec(definition.position),
    occupied: false
  };
}

function copyVec(value: Vec2): Vec2 {
  return { x: value.x, y: value.y };
}

function fromFallSpec(spec: FallMapSpecJson): BattleMapConfig {
  return {
    mapId: spec.mapId,
    displayName: spec.displayName,
    themeId: spec.themeId,
    worldSize: copyVec(spec.worldSize),
    sourceCrop: spec.sourceCrop,
    heroDefinitions: spec.heroDefinitions.map((definition) => ({ ...definition, position: copyVec(definition.position) })),
    heroSpawnPoints: spec.heroDefinitions.map((definition) => copyVec(definition.position)),
    innerObstacles: buildInnerObstacles(spec),
    terrainPatches: spec.terrainPatches,
    trees: spec.trees,
    decorativeObstacles: spec.obstacles,
    buildings: spec.buildings,
    weaponPickupDefinitions: spec.weaponPickups.map((definition) => ({ ...definition, position: copyVec(definition.position) })),
    itemPickupDefinitions: spec.itemPickups.map((definition) => ({ ...definition, position: copyVec(definition.position) })),
    weaponPickupSpawnPoints: spec.weaponPickups.map(pickupSpawnPoint),
    itemPickupSpawnPoints: spec.itemPickups.map(itemSpawnPoint)
  };
}

export const FALL_HUNT_BATTLE_MAP = fromFallSpec(FALL_HUNT_MAP_SPEC);

function legacyObstacle(
  obstacleId: string,
  kind: "wall" | "crate",
  position: Vec2,
  size: Vec2
): ArenaObstacle {
  return {
    obstacleId,
    kind,
    position,
    size,
    shape: { kind: "aabb", size }
  };
}

const INDUSTRIAL_HERO_DEFINITIONS = [
  { heroId: "player-1", displayName: "Player 1", position: { x: 704, y: 800 } },
  { heroId: "bot-1", displayName: "Bot 1", position: { x: 512, y: 544 } },
  { heroId: "bot-2", displayName: "Bot 2", position: { x: 512, y: 1056 } },
  { heroId: "bot-3", displayName: "Bot 3", position: { x: 1600, y: 320 } },
  { heroId: "bot-4", displayName: "Bot 4", position: { x: 1600, y: 1280 } },
  { heroId: "bot-5", displayName: "Bot 5", position: { x: 2048, y: 800 } }
] as const satisfies ReadonlyArray<Readonly<HeroDefinition>>;

const INDUSTRIAL_HERO_SPAWN_POINTS = INDUSTRIAL_HERO_DEFINITIONS.map((definition) => definition.position);

const INDUSTRIAL_INNER_OBSTACLES = [
  legacyObstacle("cover-nw-1", "wall", { x: 416, y: 416 }, { x: 64, y: 64 }),
  legacyObstacle("cover-nw-2", "wall", { x: 480, y: 416 }, { x: 64, y: 64 }),
  legacyObstacle("cover-nw-3", "wall", { x: 416, y: 480 }, { x: 64, y: 64 }),
  legacyObstacle("cover-ne-1", "wall", { x: 2144, y: 416 }, { x: 64, y: 64 }),
  legacyObstacle("cover-ne-2", "wall", { x: 2080, y: 416 }, { x: 64, y: 64 }),
  legacyObstacle("cover-ne-3", "wall", { x: 2144, y: 480 }, { x: 64, y: 64 }),
  legacyObstacle("cover-sw-1", "wall", { x: 416, y: 1184 }, { x: 64, y: 64 }),
  legacyObstacle("cover-sw-2", "wall", { x: 480, y: 1184 }, { x: 64, y: 64 }),
  legacyObstacle("cover-sw-3", "wall", { x: 416, y: 1120 }, { x: 64, y: 64 }),
  legacyObstacle("cover-se-1", "wall", { x: 2144, y: 1184 }, { x: 64, y: 64 }),
  legacyObstacle("cover-se-2", "wall", { x: 2080, y: 1184 }, { x: 64, y: 64 }),
  legacyObstacle("cover-se-3", "wall", { x: 2144, y: 1120 }, { x: 64, y: 64 }),
  legacyObstacle("center-top-1", "wall", { x: 1184, y: 448 }, { x: 64, y: 64 }),
  legacyObstacle("center-top-2", "wall", { x: 1248, y: 448 }, { x: 64, y: 64 }),
  legacyObstacle("center-top-3", "wall", { x: 1312, y: 448 }, { x: 64, y: 64 }),
  legacyObstacle("center-top-4", "wall", { x: 1376, y: 448 }, { x: 64, y: 64 }),
  legacyObstacle("center-bot-1", "wall", { x: 1184, y: 1152 }, { x: 64, y: 64 }),
  legacyObstacle("center-bot-2", "wall", { x: 1248, y: 1152 }, { x: 64, y: 64 }),
  legacyObstacle("center-bot-3", "wall", { x: 1312, y: 1152 }, { x: 64, y: 64 }),
  legacyObstacle("center-bot-4", "wall", { x: 1376, y: 1152 }, { x: 64, y: 64 }),
  legacyObstacle("lane-left-1", "wall", { x: 928, y: 640 }, { x: 64, y: 64 }),
  legacyObstacle("lane-left-2", "wall", { x: 928, y: 704 }, { x: 64, y: 64 }),
  legacyObstacle("lane-left-3", "wall", { x: 928, y: 896 }, { x: 64, y: 64 }),
  legacyObstacle("lane-left-4", "wall", { x: 928, y: 960 }, { x: 64, y: 64 }),
  legacyObstacle("lane-right-1", "wall", { x: 1632, y: 640 }, { x: 64, y: 64 }),
  legacyObstacle("lane-right-2", "wall", { x: 1632, y: 704 }, { x: 64, y: 64 }),
  legacyObstacle("lane-right-3", "wall", { x: 1632, y: 896 }, { x: 64, y: 64 }),
  legacyObstacle("lane-right-4", "wall", { x: 1632, y: 960 }, { x: 64, y: 64 }),
  legacyObstacle("crate-mid-top-left", "crate", { x: 1184, y: 736 }, { x: 48, y: 48 }),
  legacyObstacle("crate-mid-top-right", "crate", { x: 1376, y: 736 }, { x: 48, y: 48 }),
  legacyObstacle("crate-mid-bottom-left", "crate", { x: 1184, y: 864 }, { x: 48, y: 48 }),
  legacyObstacle("crate-mid-bottom-right", "crate", { x: 1376, y: 864 }, { x: 48, y: 48 }),
  legacyObstacle("mid-west-1", "wall", { x: 640, y: 704 }, { x: 64, y: 64 }),
  legacyObstacle("mid-west-2", "wall", { x: 640, y: 896 }, { x: 64, y: 64 }),
  legacyObstacle("mid-east-1", "wall", { x: 1920, y: 704 }, { x: 64, y: 64 }),
  legacyObstacle("mid-east-2", "wall", { x: 1920, y: 896 }, { x: 64, y: 64 }),
  legacyObstacle("lane-top-left", "wall", { x: 1056, y: 608 }, { x: 64, y: 64 }),
  legacyObstacle("lane-top-right", "wall", { x: 1504, y: 608 }, { x: 64, y: 64 }),
  legacyObstacle("lane-bottom-left", "wall", { x: 1056, y: 992 }, { x: 64, y: 64 }),
  legacyObstacle("lane-bottom-right", "wall", { x: 1504, y: 992 }, { x: 64, y: 64 }),
  legacyObstacle("crate-west-top", "crate", { x: 768, y: 640 }, { x: 48, y: 48 }),
  legacyObstacle("crate-west-bottom", "crate", { x: 768, y: 960 }, { x: 48, y: 48 }),
  legacyObstacle("crate-east-top", "crate", { x: 1792, y: 640 }, { x: 48, y: 48 }),
  legacyObstacle("crate-east-bottom", "crate", { x: 1792, y: 960 }, { x: 48, y: 48 })
] as const satisfies readonly ArenaObstacle[];

const INDUSTRIAL_WEAPON_PICKUP_DEFINITIONS = [
  { pickupId: "pickup-rocket-1", weaponKind: "RocketLauncher", position: { x: 1280, y: 256 } },
  { pickupId: "pickup-gatling-1", weaponKind: "Gatling", position: { x: 704, y: 800 } },
  { pickupId: "pickup-shotgun-1", weaponKind: "Shotgun", position: { x: 1856, y: 800 } },
  { pickupId: "pickup-rocket-2", weaponKind: "RocketLauncher", position: { x: 1280, y: 1344 } },
  { pickupId: "pickup-gatling-2", weaponKind: "Gatling", position: { x: 448, y: 800 } },
  { pickupId: "pickup-shotgun-2", weaponKind: "Shotgun", position: { x: 2112, y: 800 } }
] as const satisfies ReadonlyArray<Readonly<WeaponPickupDefinition>>;

const INDUSTRIAL_ITEM_PICKUP_DEFINITIONS = [
  { pickupId: "pickup-medkit-1", kind: "Medkit", position: { x: 960, y: 608 } },
  { pickupId: "pickup-medkit-2", kind: "Medkit", position: { x: 1600, y: 992 } }
] as const satisfies ReadonlyArray<Readonly<ItemPickupDefinition>>;

export const INDUSTRIAL_BATTLE_MAP = {
  mapId: "default-industrial-arena",
  displayName: "默认模式",
  themeId: "industrial",
  worldSize: { x: 2560, y: 1600 },
  sourceCrop: {
    sourceMapId: "slay-industrial",
    sourceSize: { x: 2560, y: 1600 },
    crop: { x: 0, y: 0, width: 2560, height: 1600 },
    scale: 1,
    offset: { x: 0, y: 0 }
  },
  heroDefinitions: INDUSTRIAL_HERO_DEFINITIONS,
  heroSpawnPoints: INDUSTRIAL_HERO_SPAWN_POINTS,
  innerObstacles: INDUSTRIAL_INNER_OBSTACLES,
  terrainPatches: [],
  trees: [],
  decorativeObstacles: [],
  buildings: [],
  weaponPickupDefinitions: INDUSTRIAL_WEAPON_PICKUP_DEFINITIONS,
  itemPickupDefinitions: INDUSTRIAL_ITEM_PICKUP_DEFINITIONS,
  weaponPickupSpawnPoints: INDUSTRIAL_WEAPON_PICKUP_DEFINITIONS.map(pickupSpawnPoint),
  itemPickupSpawnPoints: INDUSTRIAL_ITEM_PICKUP_DEFINITIONS.map(itemSpawnPoint)
} as const satisfies BattleMapConfig;

export const DEFAULT_BATTLE_MAP = INDUSTRIAL_BATTLE_MAP;

export const BATTLE_MAPS = {
  [INDUSTRIAL_BATTLE_MAP.mapId]: INDUSTRIAL_BATTLE_MAP,
  [FALL_HUNT_BATTLE_MAP.mapId]: FALL_HUNT_BATTLE_MAP
} as const satisfies Readonly<Record<string, BattleMapConfig>>;

export type BattlePlayModeId = "default" | "autumn";

export interface BattlePlayModeOption {
  modeId: BattlePlayModeId;
  label: string;
  mapId: string;
  mapLabel: string;
}

export const DEFAULT_BATTLE_MODE_ID: BattlePlayModeId = "default";

export const BATTLE_PLAY_MODE_OPTIONS = [
  {
    modeId: "default",
    label: "默认模式",
    mapId: INDUSTRIAL_BATTLE_MAP.mapId,
    mapLabel: "默认地图"
  },
  {
    modeId: "autumn",
    label: "秋季模式",
    mapId: FALL_HUNT_BATTLE_MAP.mapId,
    mapLabel: "秋季地图"
  }
] as const satisfies readonly BattlePlayModeOption[];

let activeBattleMap: BattleMapConfig = DEFAULT_BATTLE_MAP;

export function getActiveBattleMap(): BattleMapConfig {
  return activeBattleMap;
}

export function setActiveBattleMap(mapId: string | null | undefined): BattleMapConfig {
  activeBattleMap = resolveBattleMap(mapId);
  return activeBattleMap;
}

export function resolveBattleMap(mapId: string | null | undefined): BattleMapConfig {
  const normalizedMapId = mapId?.trim() ?? "";
  return normalizedMapId && Object.prototype.hasOwnProperty.call(BATTLE_MAPS, normalizedMapId)
    ? BATTLE_MAPS[normalizedMapId as keyof typeof BATTLE_MAPS]
    : DEFAULT_BATTLE_MAP;
}

export function resolveBattlePlayMode(modeId: string | null | undefined): BattlePlayModeOption {
  const normalizedModeId = modeId?.trim() ?? "";
  return BATTLE_PLAY_MODE_OPTIONS.find((option) => option.modeId === normalizedModeId) ?? BATTLE_PLAY_MODE_OPTIONS[0];
}

export function resolveMapIdForBattleMode(modeId: string | null | undefined): string {
  return resolveBattlePlayMode(modeId).mapId;
}

export function inferBattleModeIdFromMapId(mapId: string | null | undefined): BattlePlayModeId {
  const normalizedMapId = mapId?.trim() ?? "";
  return (
    BATTLE_PLAY_MODE_OPTIONS.find((option) => option.mapId === normalizedMapId)?.modeId ?? DEFAULT_BATTLE_MODE_ID
  );
}
