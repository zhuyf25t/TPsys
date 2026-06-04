import type {
  BattleExtractionZoneDefinition,
  BattleGasPlanDefinition,
  BattleLootCacheDefinition
} from "../../../extraction/objects/extraction/BattleExtractionDefinitions";
import type { BattleItemPickupState, BattleWeaponPickupState } from "../../../abilities/objects/pickup/BattlePickupState";
import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";
import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type { PickupSpawnPoint } from "./PickupSpawnPoint";

export type { PickupSpawnPoint } from "./PickupSpawnPoint";
export type Vec2 = BattleVector2;

export type BattleMapId = string;
export type BattleMapThemeId = "industrial" | "fall" | "winter" | "normal";

export type ArenaObstacleKind = "wall" | "crate" | "tree-trunk" | "building-wall" | "rock" | "logs" | "hay" | "stump";

export type ArenaObstacleShape =
  | { kind: "aabb"; size: Vec2 }
  | { kind: "circle"; radius: number };

export type CollisionShape = ArenaObstacleShape;

export type CollisionShapeSpec =
  | { kind: "aabb"; position?: Vec2; size: Vec2 }
  | { kind: "circle"; position?: Vec2; radius: number };

export interface ArenaObstacle {
  obstacleId: string;
  kind: ArenaObstacleKind;
  position: Vec2;
  size: Vec2;
  shape: ArenaObstacleShape;
  texture?: string;
  displaySize?: Vec2;
  rotation?: number;
}

export type PickupKind = "Medkit" | "Weapon";

export interface BattlePickupDefinition {
  pickupId: string;
  pickupKind: PickupKind;
  weaponKind: WeaponKind | null;
  position: Vec2;
}

export interface BattleLoadedMapSpec {
  mapId: BattleMapId;
  themeId: BattleMapThemeId;
  worldSize: Vec2;
  spawnPoints: readonly Vec2[];
  collisionObstacles: readonly ArenaObstacle[];
  pickupDefinitions: readonly BattlePickupDefinition[];
  extractionZones: ReadonlyArray<Readonly<BattleExtractionZoneDefinition>>;
  lootCaches: ReadonlyArray<Readonly<BattleLootCacheDefinition>>;
  gasPlan: Readonly<BattleGasPlanDefinition> | null;
}

export interface HeroDefinition {
  heroId: string;
  displayName: string;
  position: Vec2;
}

export interface WeaponPickupDefinition {
  pickupId: string;
  weaponKind: BattleWeaponPickupState["weaponKind"];
  position: Vec2;
}

export interface ItemPickupDefinition {
  pickupId: string;
  kind: BattleItemPickupState["kind"];
  position: Vec2;
}

export type ExtractionZoneDefinition = BattleExtractionZoneDefinition;
export type LootCacheDefinition = BattleLootCacheDefinition;
export type GasPlanDefinition = BattleGasPlanDefinition;

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
  rotation?: number;
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

export interface BattleMapConfig extends BattleLoadedMapSpec {
  displayName: string;
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

export interface BattleMapSpecJson {
  mapId: BattleMapId;
  displayName: string;
  themeId: BattleMapThemeId;
  worldSize: Vec2;
  sourceCrop: SourceCropDefinition;
  heroDefinitions: HeroDefinition[];
  terrainPatches: TerrainPatchDefinition[];
  trees: MapTreeDefinition[];
  obstacles: MapObstacleDefinition[];
  buildings: MapBuildingDefinition[];
  weaponPickups: WeaponPickupDefinition[];
  itemPickups: ItemPickupDefinition[];
  extractionZones?: ExtractionZoneDefinition[];
  lootCaches?: LootCacheDefinition[];
  gasPlan?: GasPlanDefinition;
}

export interface BattlePlayModeOption {
  modeId: string;
  label: string;
  mapId: string;
  mapLabel: string;
}
