import type Phaser from "phaser";
import {
  ALT_GRASS_TEXTURE_KEY,
  ASSET_PATHS,
  BULLET_TEXTURE_KEY,
  BUSH_TEXTURE_KEY,
  CRATE_TEXTURE_KEY,
  DIRT_TEXTURE_KEY,
  FLOOR_TEXTURE_KEY,
  GATLING_PICKUP_TEXTURE_KEY,
  OUTSIDE_TEXTURE_KEY,
  PISTOL_PICKUP_TEXTURE_KEY,
  ROCK_TEXTURE_KEY,
  ROCKET_PICKUP_TEXTURE_KEY,
  ROCKET_TEXTURE_KEY,
  SHOTGUN_PICKUP_TEXTURE_KEY,
  STONE_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  TREE_TEXTURE_KEY,
  WALL_TEXTURE_KEY,
  WATER_TEXTURE_KEY,
  WOOD_TEXTURE_KEY
} from "../../../game/constants";

export function preloadBattleAssets(scene: Phaser.Scene): void {
  scene.load.image(FLOOR_TEXTURE_KEY, ASSET_PATHS.floor);
  scene.load.image(ALT_GRASS_TEXTURE_KEY, ASSET_PATHS.altGrass);
  scene.load.image(DIRT_TEXTURE_KEY, ASSET_PATHS.dirt);
  scene.load.image(STONE_TEXTURE_KEY, ASSET_PATHS.stone);
  scene.load.image(STONE_TRIM_TEXTURE_KEY, ASSET_PATHS.stoneTrim);
  scene.load.image(WOOD_TEXTURE_KEY, ASSET_PATHS.wood);
  scene.load.image(WATER_TEXTURE_KEY, ASSET_PATHS.water);
  scene.load.image(OUTSIDE_TEXTURE_KEY, ASSET_PATHS.outside);
  scene.load.image(BUSH_TEXTURE_KEY, ASSET_PATHS.bush);
  scene.load.image(TREE_TEXTURE_KEY, ASSET_PATHS.tree);
  scene.load.image(ROCK_TEXTURE_KEY, ASSET_PATHS.rock);
  scene.load.image(WALL_TEXTURE_KEY, ASSET_PATHS.wall);
  scene.load.image(CRATE_TEXTURE_KEY, ASSET_PATHS.crate);
  scene.load.image(BULLET_TEXTURE_KEY, ASSET_PATHS.bullet);
  scene.load.image(ROCKET_TEXTURE_KEY, ASSET_PATHS.rocket);
  scene.load.image(PISTOL_PICKUP_TEXTURE_KEY, ASSET_PATHS.pickupPistol);
  scene.load.image(ROCKET_PICKUP_TEXTURE_KEY, ASSET_PATHS.pickupRocket);
  scene.load.image(GATLING_PICKUP_TEXTURE_KEY, ASSET_PATHS.pickupGatling);
  scene.load.image(SHOTGUN_PICKUP_TEXTURE_KEY, ASSET_PATHS.pickupShotgun);
  scene.load.image("hero-player", ASSET_PATHS.player);
  scene.load.image("hero-survivor", ASSET_PATHS.survivor);
  scene.load.image("hero-soldier", ASSET_PATHS.soldier);
  scene.load.image("hero-brown", ASSET_PATHS.brown);
  scene.load.image("hero-old", ASSET_PATHS.old);
  scene.load.image("hero-woman", ASSET_PATHS.woman);
  scene.load.image("hero-hitman", ASSET_PATHS.hitman);
  scene.load.image("hero-robot", ASSET_PATHS.robot);
  scene.load.image("hero-zombie", ASSET_PATHS.zombie);
}
