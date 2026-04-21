import type { TeamMode, Vec2, WeaponKind } from "../domain/types";

export const GAME_WIDTH = 1280;
export const GAME_HEIGHT = 720;

export const WORLD_SIZE: Vec2 = {
  x: 2560,
  y: 1600
};
export const GLOBAL_BACKGROUND_PADDING = 600;

export const TEAM_MODE: TeamMode = "FreeForAll";
export const FLOOR_TILE_SIZE = 64;
export const HERO_RADIUS = 18;
export const HERO_SPRITE_SCALE = 1.35;
export const HERO_MAX_HP = 100;
export const HERO_MAX_STAMINA = 100;
export const BASE_MOVE_SPEED = 255;
export const SPRINT_MULTIPLIER = 1.55;
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
  floor: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_02.png",
  altGrass: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_17.png",
  dirt: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_15.png",
  stone: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_11.png",
  stoneTrim: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_271.png",
  wood: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_101.png",
  water: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_19.png",
  outside: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_04.png",
  bush: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_235.png",
  tree: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_240.png",
  rock: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_237.png",
  wall: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_141.png",
  crate: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_129.png",
  bullet: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_316.png",
  rocket: "/assets/kenney-top-down-shooter/PNG/Tiles/tile_318.png",
  pickupPistol: "/assets/kenney-top-down-shooter/PNG/weapon_gun.png",
  pickupGatling: "/assets/kenney-top-down-shooter/PNG/weapon_machine.png",
  pickupShotgun: "/assets/kenney-top-down-shooter/PNG/weapon_silencer.png",
  pickupRocket: "/assets/kenney-top-down-shooter/PNG/weapon_machine.png",
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

export interface ArenaObstacle {
  obstacleId: string;
  kind: "wall" | "crate";
  position: Vec2;
  size: Vec2;
}

export const INNER_OBSTACLES: readonly ArenaObstacle[] = [
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
] as const;

export const WEAPON_PICKUP_ICON_KEYS: Record<WeaponKind, string> = {
  Pistol: PISTOL_PICKUP_TEXTURE_KEY,
  RocketLauncher: ROCKET_PICKUP_TEXTURE_KEY,
  Gatling: GATLING_PICKUP_TEXTURE_KEY,
  Shotgun: SHOTGUN_PICKUP_TEXTURE_KEY
};
