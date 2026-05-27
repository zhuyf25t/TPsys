import type { TeamMode, Vec2, WeaponKind } from "../../../objects/battle/types";
import { DEFAULT_BATTLE_MAP, getActiveBattleMap, type ArenaObstacle } from "./maps/battleMapCatalog";

export {
  BATTLE_PLAY_MODE_OPTIONS,
  DEFAULT_BATTLE_MAP,
  DEFAULT_BATTLE_MODE_ID,
  getActiveBattleMap,
  inferBattleModeIdFromMapId,
  isNaturalBattleMapTheme,
  resolveBattleMap,
  resolveBattlePlayMode,
  resolveMapIdForBattleMode,
  setActiveBattleMap
} from "./maps/battleMapCatalog";
export type { ArenaObstacle, BattleMapThemeId } from "./maps/battleMapCatalog";

export const GAME_WIDTH = 1280;
export const GAME_HEIGHT = 720;

export const WORLD_SIZE: Vec2 = {
  get x() {
    return getActiveBattleMap().worldSize.x;
  },
  get y() {
    return getActiveBattleMap().worldSize.y;
  }
};
export const GLOBAL_BACKGROUND_PADDING = 600;

export const TEAM_MODE: TeamMode = "FreeForAll";
export const FLOOR_TILE_SIZE = 64;
export const HERO_RADIUS = 18;
export const HERO_SPRITE_SCALE = 1.35;
export const HERO_MAX_HP = 100;
export const HERO_MAX_STAMINA = 100;
export const BASE_MOVE_SPEED = 255;
export const SPRINT_MULTIPLIER = 1.75;
export const STAMINA_DRAIN_PER_SECOND = 38;
export const STAMINA_RECOVER_PER_SECOND = 24;
export const WEAPON_PICKUP_RADIUS = 84;
export const AUTO_PICKUP_RADIUS = 40;
export const RESPAWN_DELAY_MS = 3000;
export const WEAPON_PICKUP_RESPAWN_MS = 10000;
export const FEED_EVENT_TTL_MS = 6000;
export const JUMP_DISTANCE = 90;
export const JUMP_COOLDOWN_MS = 2000;
export const JUMP_STAMINA_COST = 20;
export const WEAPON_SWITCH_MS = 280;

export const FLOOR_TEXTURE_KEY = "floor";
export const ALT_GRASS_TEXTURE_KEY = "alt-grass";
export const DIRT_TEXTURE_KEY = "dirt";
export const STONE_TEXTURE_KEY = "stone";
export const STONE_TRIM_TEXTURE_KEY = "stone-trim";
export const WOOD_TEXTURE_KEY = "wood";
export const WATER_TEXTURE_KEY = "water";
export const OUTSIDE_TEXTURE_KEY = "outside";
export const BUSH_TEXTURE_KEY = "bush";
export const TREE_TEXTURE_KEY = "tree";
export const ROCK_TEXTURE_KEY = "rock";
export const WALL_TEXTURE_KEY = "wall";
export const CRATE_TEXTURE_KEY = "crate";
export const BULLET_TEXTURE_KEY = "bullet";
export const ROCKET_TEXTURE_KEY = "rocket";
export const PISTOL_PICKUP_TEXTURE_KEY = "pickup-pistol";
export const GATLING_PICKUP_TEXTURE_KEY = "pickup-gatling";
export const SHOTGUN_PICKUP_TEXTURE_KEY = "pickup-shotgun";
export const ROCKET_PICKUP_TEXTURE_KEY = "pickup-rocket";
export const PISTOL_WORLD_TEXTURE_KEY = "weapon-world-pistol";
export const GATLING_WORLD_TEXTURE_KEY = "weapon-world-gatling";
export const SHOTGUN_WORLD_TEXTURE_KEY = "weapon-world-shotgun";
export const ROCKET_WORLD_TEXTURE_KEY = "weapon-world-rocket";
export const PLAYER_TEXTURE_KEY = "hero-player";
export const SURVIVOR_TEXTURE_KEY = "hero-survivor";
export const SOLDIER_TEXTURE_KEY = "hero-soldier";
export const BROWN_TEXTURE_KEY = "hero-brown";
export const OLD_TEXTURE_KEY = "hero-old";
export const WOMAN_TEXTURE_KEY = "hero-woman";
export const HITMAN_TEXTURE_KEY = "hero-hitman";
export const ROBOT_TEXTURE_KEY = "hero-robot";
export const ZOMBIE_TEXTURE_KEY = "hero-zombie";

export const ASSET_PATHS = {
  floor: "/assets/battle/arena/metal_floor_tile.svg",
  altGrass: "/assets/battle/arena/metal_floor_tile.svg",
  dirt: "/assets/battle/arena/void_tile.svg",
  stone: "/assets/battle/arena/panel_tile.svg",
  stoneTrim: "/assets/battle/arena/stone_trim_tile.svg",
  wood: "/assets/battle/arena/panel_tile.svg",
  water: "/assets/battle/arena/void_tile.svg",
  outside: "/assets/battle/arena/void_tile.svg",
  bush: "/assets/battle/arena/metal_debris_rock.svg",
  tree: "/assets/battle/arena/wall_segment.svg",
  rock: "/assets/battle/arena/metal_debris_rock.svg",
  wall: "/assets/battle/arena/wall_segment.svg",
  crate: "/assets/battle/arena/sealed_crate.svg",
  bullet: "/assets/battle/projectiles/energy_bullet.svg",
  rocket: "/assets/battle/projectiles/rocket_shell.svg",
  pickupPistol: "/assets/battle/weapons/deagle.svg",
  pickupGatling: "/assets/battle/weapons/acr.svg",
  pickupShotgun: "/assets/battle/weapons/badlander.svg",
  pickupRocket: "/assets/battle/weapons/m202.svg",
  weaponPistolWorld: "/assets/battle/weapons/deagle_world.svg",
  weaponGatlingWorld: "/assets/battle/weapons/acr_world.svg",
  weaponShotgunWorld: "/assets/battle/weapons/badlander_world.svg",
  weaponRocketWorld: "/assets/battle/weapons/m202_world.svg",
  player: "/assets/kenney-top-down-shooter/PNG/Man Blue/manBlue_hold.png",
  survivor: "/assets/kenney-top-down-shooter/PNG/Survivor 1/survivor1_hold.png",
  soldier: "/assets/kenney-top-down-shooter/PNG/Soldier 1/soldier1_hold.png",
  brown: "/assets/kenney-top-down-shooter/PNG/Man Brown/manBrown_hold.png",
  old: "/assets/kenney-top-down-shooter/PNG/Man Old/manOld_hold.png",
  woman: "/assets/kenney-top-down-shooter/PNG/Woman Green/womanGreen_hold.png",
  hitman: "/assets/kenney-top-down-shooter/PNG/Hitman 1/hitman1_hold.png",
  robot: "/assets/kenney-top-down-shooter/PNG/Robot 1/robot1_hold.png",
  zombie: "/assets/kenney-top-down-shooter/PNG/Zombie 1/zoimbie1_hold.png"
} as const;

export const FALL_ASSET_PATHS = {
  "fall-big-oak-trunk-1": "/assets/battle/fall/obstacles/big_oak_tree_trunk_1.svg",
  "fall-big-oak-trunk-2": "/assets/battle/fall/obstacles/big_oak_tree_trunk_2.svg",
  "fall-big-oak-trunk-3": "/assets/battle/fall/obstacles/big_oak_tree_trunk_3.svg",
  "fall-big-oak-trunk-4": "/assets/battle/fall/obstacles/big_oak_tree_trunk_4.svg",
  "fall-big-oak-trunk-5": "/assets/battle/fall/obstacles/big_oak_tree_trunk_5.svg",
  "fall-big-oak-trunk-6": "/assets/battle/fall/obstacles/big_oak_tree_trunk_6.svg",
  "fall-big-oak-leaves-1": "/assets/battle/fall/obstacles/big_oak_tree_leaves_1.svg",
  "fall-big-oak-leaves-2": "/assets/battle/fall/obstacles/big_oak_tree_leaves_2.svg",
  "fall-big-oak-leaves-3": "/assets/battle/fall/obstacles/big_oak_tree_leaves_3.svg",
  "fall-big-oak-leaves-4": "/assets/battle/fall/obstacles/big_oak_tree_leaves_4.svg",
  "fall-big-oak-leaves-5": "/assets/battle/fall/obstacles/big_oak_tree_leaves_5.svg",
  "fall-big-oak-leaves-6": "/assets/battle/fall/obstacles/big_oak_tree_leaves_6.svg",
  "fall-maple-trunk": "/assets/battle/fall/obstacles/maple_tree_trunk.svg",
  "fall-maple-leaves-1": "/assets/battle/fall/obstacles/maple_tree_leaves_1.svg",
  "fall-maple-leaves-2": "/assets/battle/fall/obstacles/maple_tree_leaves_2.svg",
  "fall-maple-leaves-3": "/assets/battle/fall/obstacles/maple_tree_leaves_3.svg",
  "fall-birch-leaves-1": "/assets/battle/fall/obstacles/birch_tree_leaves_1.svg",
  "fall-birch-leaves-2": "/assets/battle/fall/obstacles/birch_tree_leaves_2.svg",
  "fall-oak-leaves-1": "/assets/battle/fall/obstacles/oak_tree_leaves_1.svg",
  "fall-oak-leaves-2": "/assets/battle/fall/obstacles/oak_tree_leaves_2.svg",
  "fall-clearing-boulder-1": "/assets/battle/fall/obstacles/clearing_boulder_1.svg",
  "fall-clearing-boulder-2": "/assets/battle/fall/obstacles/clearing_boulder_2.svg",
  "fall-vibrant-bush-1": "/assets/battle/fall/obstacles/vibrant_bush_1.svg",
  "fall-vibrant-bush-2": "/assets/battle/fall/obstacles/vibrant_bush_2.svg",
  "fall-vibrant-bush-3": "/assets/battle/fall/obstacles/vibrant_bush_3.svg",
  "fall-hay-bale": "/assets/battle/fall/obstacles/hay_bale.svg",
  "fall-large-logs-pile": "/assets/battle/fall/obstacles/large_logs_pile.svg",
  "fall-oak-leaf-pile": "/assets/battle/fall/obstacles/oak_leaf_pile.svg",
  "fall-stump": "/assets/battle/fall/obstacles/stump.svg",
  "fall-barn-floor": "/assets/battle/fall/buildings/barn_floor_1.svg",
  "fall-barn-roof": "/assets/battle/fall/buildings/barn_ceiling.svg",
  "fall-tent-floor": "/assets/battle/fall/buildings/tent_floor.svg",
  "fall-tent-roof": "/assets/battle/fall/buildings/tent_ceiling.svg",
  "fall-big-tent-floor": "/assets/battle/fall/buildings/tent_floor_big.svg",
  "fall-big-tent-roof": "/assets/battle/fall/buildings/tent_ceiling_big.svg",
  "fall-patch-floor": "/assets/battle/fall/buildings/fall_patch_floor.svg",
  "fall-hay-shed-roof-1": "/assets/battle/fall/buildings/hay_shed_ceiling_1.svg",
  "fall-hay-shed-roof-2": "/assets/battle/fall/buildings/hay_shed_ceiling_2.svg",
  "fall-door": "/assets/battle/fall/shared/door.svg"
} as const;

export const WINTER_ASSET_PATHS = {
  "winter-big-oak-1": "/assets/battle/winter/obstacles/big_oak_tree_1.svg",
  "winter-big-oak-2": "/assets/battle/winter/obstacles/big_oak_tree_2.svg",
  "winter-big-oak-3": "/assets/battle/winter/obstacles/big_oak_tree_3.svg",
  "winter-big-oak-4": "/assets/battle/winter/obstacles/big_oak_tree_4.svg",
  "winter-big-oak-5": "/assets/battle/winter/obstacles/big_oak_tree_5.svg",
  "winter-birch-1": "/assets/battle/winter/obstacles/birch_tree_1.svg",
  "winter-birch-2": "/assets/battle/winter/obstacles/birch_tree_2.svg",
  "winter-pine-tree": "/assets/battle/winter/obstacles/pine_tree.svg",
  "winter-bush": "/assets/battle/winter/obstacles/bush.svg",
  "winter-blueberry-bush": "/assets/battle/winter/obstacles/blueberry_bush.svg",
  "winter-planted-bushes": "/assets/battle/winter/obstacles/planted_bushes_winter.svg",
  "winter-rock-1": "/assets/battle/winter/obstacles/rock_1.svg",
  "winter-rock-2": "/assets/battle/winter/obstacles/rock_2.svg",
  "winter-rock-3": "/assets/battle/winter/obstacles/rock_3.svg",
  "winter-frozen-crate-1": "/assets/battle/winter/obstacles/frozen_crate_1.svg",
  "winter-red-house-roof": "/assets/battle/winter/buildings/red_house_ceiling.svg",
  "winter-green-house-roof-1": "/assets/battle/winter/buildings/green_house_ceiling_1.svg",
  "winter-igloo-roof-1": "/assets/battle/winter/buildings/igloo_ceiling_1.svg",
  "winter-igloo-floor": "/assets/battle/winter/buildings/igloo_floor.svg"
} as const;

export const NORMAL_ASSET_PATHS = {
  "normal-blue-house-floor": "/assets/battle/normal/buildings/blue_house_floor_1.svg",
  "normal-blue-house-roof": "/assets/battle/normal/buildings/blue_house_ceiling.svg",
  "normal-large-warehouse-floor": "/assets/battle/normal/buildings/large_warehouse_floor.svg",
  "normal-large-warehouse-roof": "/assets/battle/normal/buildings/large_warehouse_ceiling.svg",
  "normal-shed-floor": "/assets/battle/normal/buildings/shed_floor.svg",
  "normal-shed-roof": "/assets/battle/normal/buildings/shed_ceiling.svg",
  "normal-planted-bushes": "/assets/battle/normal/obstacles/planted_bushes.svg"
} as const;

export const SHARED_BATTLE_ASSET_PATHS = {
  "shared-door": "/assets/battle/shared/obstacles/door.svg",
  "shared-oak-trunk-1": "/assets/battle/shared/obstacles/oak_tree_trunk_1.svg",
  "shared-oak-trunk-2": "/assets/battle/shared/obstacles/oak_tree_trunk_2.svg",
  "shared-oak-leaves-1": "/assets/battle/shared/obstacles/oak_tree_leaves_1.svg",
  "shared-oak-leaves-2": "/assets/battle/shared/obstacles/oak_tree_leaves_2.svg",
  "shared-birch-trunk": "/assets/battle/shared/obstacles/birch_tree_trunk.svg",
  "shared-birch-leaves-1": "/assets/battle/shared/obstacles/birch_tree_leaves_1.svg",
  "shared-birch-leaves-2": "/assets/battle/shared/obstacles/birch_tree_leaves_2.svg",
  "shared-pine-trunk": "/assets/battle/shared/obstacles/pine_tree_trunk.svg",
  "shared-pine-tree": "/assets/battle/shared/obstacles/pine_tree.svg",
  "shared-bush": "/assets/battle/shared/obstacles/bush.svg",
  "shared-blueberry-bush": "/assets/battle/shared/obstacles/blueberry_bush.svg",
  "shared-rock-1": "/assets/battle/shared/obstacles/rock_1.svg",
  "shared-rock-2": "/assets/battle/shared/obstacles/rock_2.svg",
  "shared-rock-3": "/assets/battle/shared/obstacles/rock_3.svg",
  "shared-small-logs-pile": "/assets/battle/shared/obstacles/small_logs_pile.svg",
  "shared-red-house-floor-1": "/assets/battle/shared/buildings/red_house_floor_1.svg",
  "shared-red-house-roof": "/assets/battle/shared/buildings/red_house_ceiling.svg",
  "shared-green-house-floor-1": "/assets/battle/shared/buildings/green_house_floor_1.svg",
  "shared-green-house-roof-1": "/assets/battle/shared/buildings/green_house_ceiling_1.svg",
  "shared-warehouse-floor-1": "/assets/battle/shared/buildings/warehouse_floor_1.svg",
  "shared-warehouse-roof-1": "/assets/battle/shared/buildings/warehouse_ceiling_1.svg"
} as const;

export const MAP_THEME_ASSET_PATHS = {
  ...FALL_ASSET_PATHS,
  ...WINTER_ASSET_PATHS,
  ...NORMAL_ASSET_PATHS,
  ...SHARED_BATTLE_ASSET_PATHS
} as const;

export const HERO_TEXTURE_KEYS = {
  "player-1": PLAYER_TEXTURE_KEY,
  "bot-1": SURVIVOR_TEXTURE_KEY,
  "bot-2": SOLDIER_TEXTURE_KEY,
  "bot-3": BROWN_TEXTURE_KEY,
  "bot-4": OLD_TEXTURE_KEY,
  "bot-5": WOMAN_TEXTURE_KEY,
  "bot-6": HITMAN_TEXTURE_KEY,
  "bot-7": ROBOT_TEXTURE_KEY,
  "bot-8": ZOMBIE_TEXTURE_KEY,
  "bot-9": PLAYER_TEXTURE_KEY
} as const;

export const HERO_TINTS = {
  "player-1": 0x7ae2ff,
  "bot-1": 0x7dd87d,
  "bot-2": 0xffd36e,
  "bot-3": 0xff9d7a,
  "bot-4": 0xc8b6ff,
  "bot-5": 0x87f0d6,
  "bot-6": 0xff7aa2,
  "bot-7": 0xa3b4ff,
  "bot-8": 0xff8b94,
  "bot-9": 0xffffff
} as const;

export const INNER_OBSTACLES: readonly ArenaObstacle[] = DEFAULT_BATTLE_MAP.innerObstacles;

export const WEAPON_PICKUP_ICON_KEYS: Record<WeaponKind, string> = {
  Pistol: PISTOL_PICKUP_TEXTURE_KEY,
  RocketLauncher: ROCKET_PICKUP_TEXTURE_KEY,
  Gatling: GATLING_PICKUP_TEXTURE_KEY,
  Shotgun: SHOTGUN_PICKUP_TEXTURE_KEY
};

export const WEAPON_WORLD_TEXTURE_KEYS: Record<WeaponKind, string> = {
  Pistol: PISTOL_WORLD_TEXTURE_KEY,
  RocketLauncher: ROCKET_WORLD_TEXTURE_KEY,
  Gatling: GATLING_WORLD_TEXTURE_KEY,
  Shotgun: SHOTGUN_WORLD_TEXTURE_KEY
};
