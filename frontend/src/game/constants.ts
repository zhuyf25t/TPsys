import type { TeamMode, Vec2, WeaponKind } from "../domain/types";
import { DEFAULT_BATTLE_MAP, type ArenaObstacle } from "./battleMapCatalog";

export type { ArenaObstacle } from "./battleMapCatalog";

export const GAME_WIDTH = 1280;
export const GAME_HEIGHT = 720;

export const WORLD_SIZE: Vec2 = DEFAULT_BATTLE_MAP.worldSize;
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
  pickupPistol: "/assets/battle/pickups/pistol_icon.svg",
  pickupGatling: "/assets/battle/pickups/gatling_icon.svg",
  pickupShotgun: "/assets/battle/pickups/shotgun_icon.svg",
  pickupRocket: "/assets/battle/pickups/rocket_icon.svg",
  player: "/assets/kenney-top-down-shooter/PNG/Man Blue/manBlue_gun.png",
  survivor: "/assets/kenney-top-down-shooter/PNG/Survivor 1/survivor1_gun.png",
  soldier: "/assets/kenney-top-down-shooter/PNG/Soldier 1/soldier1_gun.png",
  brown: "/assets/kenney-top-down-shooter/PNG/Man Brown/manBrown_gun.png",
  old: "/assets/kenney-top-down-shooter/PNG/Man Old/manOld_gun.png",
  woman: "/assets/kenney-top-down-shooter/PNG/Woman Green/womanGreen_gun.png",
  hitman: "/assets/kenney-top-down-shooter/PNG/Hitman 1/hitman1_gun.png",
  robot: "/assets/kenney-top-down-shooter/PNG/Robot 1/robot1_gun.png",
  zombie: "/assets/kenney-top-down-shooter/PNG/Zombie 1/zoimbie1_gun.png"
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
