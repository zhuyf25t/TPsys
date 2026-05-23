import type Phaser from "phaser";
import {
  ALT_GRASS_TEXTURE_KEY,
  ASSET_PATHS,
  BUSH_TEXTURE_KEY,
  CRATE_TEXTURE_KEY,
  DIRT_TEXTURE_KEY,
  FALL_ASSET_PATHS,
  FLOOR_TEXTURE_KEY,
  OUTSIDE_TEXTURE_KEY,
  ROCK_TEXTURE_KEY,
  STONE_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  TREE_TEXTURE_KEY,
  WALL_TEXTURE_KEY,
  WATER_TEXTURE_KEY,
  WOOD_TEXTURE_KEY
} from "../constants";
import { PROJECTILE_RASTER_ATLAS_TEXTURE_KEY } from "./projectileRasterAtlas";
import { WEAPON_RASTER_ATLAS_TEXTURE_KEY } from "./weaponRasterAtlas";

/** 中文名：preload战斗assets（preloadBattleAssets）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
  scene.load.atlas(
    PROJECTILE_RASTER_ATLAS_TEXTURE_KEY,
    "/assets/battle/projectiles/projectile-raster-atlas.png",
    "/assets/battle/projectiles/projectile-raster-atlas.json"
  );
  scene.load.atlas(
    WEAPON_RASTER_ATLAS_TEXTURE_KEY,
    "/assets/battle/weapons/weapon-raster-atlas.png",
    "/assets/battle/weapons/weapon-raster-atlas.json"
  );
  Object.entries(FALL_ASSET_PATHS).forEach(([textureKey, path]) => {
    scene.load.image(textureKey, path);
  });
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
