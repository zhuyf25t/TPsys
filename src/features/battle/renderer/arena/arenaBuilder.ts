import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
import {
  ALT_GRASS_TEXTURE_KEY,
  CRATE_TEXTURE_KEY,
  DIRT_TEXTURE_KEY,
  FLOOR_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  GLOBAL_BACKGROUND_PADDING,
  INNER_OBSTACLES,
  OUTSIDE_TEXTURE_KEY,
  ROCK_TEXTURE_KEY,
  STONE_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  TREE_TEXTURE_KEY,
  WALL_TEXTURE_KEY,
  WATER_TEXTURE_KEY,
  WOOD_TEXTURE_KEY,
  WORLD_SIZE,
  BUSH_TEXTURE_KEY,
  type ArenaObstacle
} from "../../../../game/constants";
import { ITEM_PICKUP_SPAWN_POINTS, WEAPON_PICKUP_SPAWN_POINTS } from "../../../../game/spawn";

export type OccludableSprite = Phaser.GameObjects.Image | Phaser.Physics.Arcade.Image;

export interface ObstacleBounds {
  position: Vec2;
  size: Vec2;
}

export interface OccludableView {
  sprite: OccludableSprite;
  bounds: Phaser.Geom.Rectangle;
  baseAlpha: number;
}

export interface ArenaBuilderContext {
  scene: Phaser.Scene;
  wallBodies: Phaser.Physics.Arcade.StaticGroup;
  obstacleBounds: ObstacleBounds[];
  occludables: OccludableView[];
}

export function buildArena(context: ArenaBuilderContext): void {
  const { scene } = context;
  const extendedWidth = WORLD_SIZE.x + GLOBAL_BACKGROUND_PADDING * 2;
  const extendedHeight = WORLD_SIZE.y + GLOBAL_BACKGROUND_PADDING * 2;

  scene.cameras.main.setBackgroundColor("#0d0f0f");

  scene.add
    .tileSprite(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, extendedWidth, extendedHeight, OUTSIDE_TEXTURE_KEY)
    .setDepth(-40)
    .setTint(0x242320)
    .setAlpha(0.98);

  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, extendedWidth - 180, extendedHeight - 180, ALT_GRASS_TEXTURE_KEY, -39, 0.42);
  createPatternRect(scene, 128, 160, 680, 520, WATER_TEXTURE_KEY, -38, 0.08);
  createPatternRect(scene, WORLD_SIZE.x - 128, 160, 680, 520, WATER_TEXTURE_KEY, -38, 0.08);
  createPatternRect(scene, 128, WORLD_SIZE.y - 160, 680, 520, WATER_TEXTURE_KEY, -38, 0.08);
  createPatternRect(scene, WORLD_SIZE.x - 128, WORLD_SIZE.y - 160, 680, 520, WATER_TEXTURE_KEY, -38, 0.08);

  scene.add.tileSprite(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, WORLD_SIZE.x, WORLD_SIZE.y, FLOOR_TEXTURE_KEY).setDepth(-20).setTint(0x4f5b49);
  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, WORLD_SIZE.x - 192, WORLD_SIZE.y - 192, ALT_GRASS_TEXTURE_KEY, -19, 0.14);
  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1536, 928, STONE_TEXTURE_KEY, -18, 0.98);
  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1376, 768, STONE_TRIM_TEXTURE_KEY, -17, 0.98);
  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1184, 576, FLOOR_TEXTURE_KEY, -16, 0.18);
  createPatternRect(scene, WORLD_SIZE.x / 2, 320, 544, 160, WOOD_TEXTURE_KEY, -16, 0.88);
  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y - 320, 544, 160, WOOD_TEXTURE_KEY, -16, 0.88);
  createPatternRect(scene, 640, WORLD_SIZE.y / 2, 416, 224, DIRT_TEXTURE_KEY, -16, 0.88);
  createPatternRect(scene, WORLD_SIZE.x - 640, WORLD_SIZE.y / 2, 416, 224, DIRT_TEXTURE_KEY, -16, 0.88);
  createPatternRect(scene, 448, 304, 224, 160, WATER_TEXTURE_KEY, -15, 0.08);
  createPatternRect(scene, WORLD_SIZE.x - 448, 304, 224, 160, WATER_TEXTURE_KEY, -15, 0.08);
  createPatternRect(scene, 448, WORLD_SIZE.y - 304, 224, 160, WATER_TEXTURE_KEY, -15, 0.08);
  createPatternRect(scene, WORLD_SIZE.x - 448, WORLD_SIZE.y - 304, 224, 160, WATER_TEXTURE_KEY, -15, 0.08);

  scene.add
    .rectangle(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, WORLD_SIZE.x, WORLD_SIZE.y, 0xf5d9a2, 0.02)
    .setStrokeStyle(4, 0xc79238, 0.28)
    .setDepth(-14);

  createPickupPads(scene);
  createArenaDecorations(scene, context.occludables);
  createBorderWalls(scene, context.wallBodies, context.obstacleBounds, context.occludables);
  createInnerStructures(scene, context.wallBodies, context.obstacleBounds, context.occludables);
}

function createPatternRect(
  scene: Phaser.Scene,
  x: number,
  y: number,
  width: number,
  height: number,
  textureKey: string,
  depth: number,
  alpha: number
): void {
  scene.add.tileSprite(x, y, width, height, textureKey).setDepth(depth).setAlpha(alpha);
}

function createPickupPads(scene: Phaser.Scene): void {
  WEAPON_PICKUP_SPAWN_POINTS.forEach((point) => {
    scene.add.tileSprite(point.position.x, point.position.y + 8, 104, 78, WOOD_TEXTURE_KEY).setDepth(-12).setAlpha(0.96);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 112, 84, 0x8e6c3c, 0.12)
      .setStrokeStyle(2, 0xd1b27c, 0.72)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 92, 56, 0xe0cb98, 0.08)
      .setStrokeStyle(1, 0xf5e6bf, 0.42)
      .setDepth(-10);
  });

  ITEM_PICKUP_SPAWN_POINTS.forEach((point) => {
    scene.add
      .tileSprite(point.position.x, point.position.y + 8, 92, 72, ALT_GRASS_TEXTURE_KEY)
      .setDepth(-12)
      .setTint(0x9df5b5)
      .setAlpha(0.92);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 100, 78, 0x3ba85c, 0.14)
      .setStrokeStyle(2, 0x9df5b5, 0.76)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 72, 44, 0xe6fff0, 0.08)
      .setStrokeStyle(1, 0xe8fff1, 0.36)
      .setDepth(-10);
  });
}

function createArenaDecorations(scene: Phaser.Scene, occludables: OccludableView[]): void {
  const trees: readonly Vec2[] = [
    { x: 320, y: 320 },
    { x: 2240, y: 320 },
    { x: 320, y: 1280 },
    { x: 2240, y: 1280 },
    { x: 736, y: 224 },
    { x: 1824, y: 224 },
    { x: 736, y: 1376 },
    { x: 1824, y: 1376 }
  ];

  const rocks: readonly Vec2[] = [
    { x: 896, y: 448 },
    { x: 1664, y: 448 },
    { x: 896, y: 1152 },
    { x: 1664, y: 1152 },
    { x: 640, y: 640 },
    { x: 1920, y: 640 },
    { x: 640, y: 960 },
    { x: 1920, y: 960 }
  ];

  const bushes: readonly Vec2[] = [
    { x: 544, y: 576 },
    { x: 2016, y: 576 },
    { x: 544, y: 1024 },
    { x: 2016, y: 1024 },
    { x: 1280, y: 224 },
    { x: 1280, y: 1376 }
  ];

  trees.forEach((position) => {
    const tree = scene.add.image(position.x, position.y, TREE_TEXTURE_KEY).setScale(1.35).setDepth(54).setAlpha(0.95);
    registerOccludable(tree, 0.95, occludables);
  });

  rocks.forEach((position) => {
    const rock = scene.add.image(position.x, position.y, ROCK_TEXTURE_KEY).setScale(1.18).setDepth(53).setAlpha(0.95);
    registerOccludable(rock, 0.95, occludables);
  });

  bushes.forEach((position) => {
    scene.add.image(position.x, position.y, BUSH_TEXTURE_KEY).setScale(1.3).setDepth(11).setAlpha(0.9);
  });
}

function createBorderWalls(scene: Phaser.Scene, wallBodies: Phaser.Physics.Arcade.StaticGroup, obstacleBounds: ObstacleBounds[], occludables: OccludableView[]): void {
  for (let x = FLOOR_TILE_SIZE / 2; x < WORLD_SIZE.x; x += FLOOR_TILE_SIZE) {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-top-${x}`,
      kind: "wall",
      position: { x, y: FLOOR_TILE_SIZE / 2 },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE }
    });
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-bottom-${x}`,
      kind: "wall",
      position: { x, y: WORLD_SIZE.y - FLOOR_TILE_SIZE / 2 },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE }
    });
  }

  for (let y = FLOOR_TILE_SIZE * 1.5; y < WORLD_SIZE.y - FLOOR_TILE_SIZE / 2; y += FLOOR_TILE_SIZE) {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-left-${y}`,
      kind: "wall",
      position: { x: FLOOR_TILE_SIZE / 2, y },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE }
    });
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-right-${y}`,
      kind: "wall",
      position: { x: WORLD_SIZE.x - FLOOR_TILE_SIZE / 2, y },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE }
    });
  }
}

function createInnerStructures(
  scene: Phaser.Scene,
  wallBodies: Phaser.Physics.Arcade.StaticGroup,
  obstacleBounds: ObstacleBounds[],
  occludables: OccludableView[]
): void {
  scene.add.rectangle(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1504, 864, 0x10151a, 0.08).setDepth(-14);
  scene.add.rectangle(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1320, 680, 0xf7e4ba, 0.02).setDepth(-13);

  INNER_OBSTACLES.forEach((obstacle) => {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, obstacle);
  });
}

function createStaticObstacle(
  scene: Phaser.Scene,
  wallBodies: Phaser.Physics.Arcade.StaticGroup,
  obstacleBounds: ObstacleBounds[],
  occludables: OccludableView[],
  obstacle: ArenaObstacle
): void {
  const textureKey = obstacle.kind === "wall" ? WALL_TEXTURE_KEY : CRATE_TEXTURE_KEY;
  const staticImage = scene.physics.add
    .staticImage(obstacle.position.x, obstacle.position.y, textureKey)
    .setDepth(obstacle.kind === "wall" ? 54 : 44);

  if (obstacle.kind === "crate") {
    staticImage.setScale(0.85);
    staticImage.setDepth(46);
  }

  staticImage.refreshBody();
  wallBodies.add(staticImage);
  obstacleBounds.push({
    position: { x: staticImage.x, y: staticImage.y },
    size: { x: staticImage.displayWidth, y: staticImage.displayHeight }
  });

  if (obstacle.kind === "wall") {
    registerOccludable(staticImage, 1, occludables);
  }
}

function registerOccludable(sprite: OccludableSprite, baseAlpha: number, occludables: OccludableView[]): void {
  const bounds = sprite.getBounds();
  occludables.push({
    sprite,
    bounds: new Phaser.Geom.Rectangle(bounds.x, bounds.y, bounds.width, bounds.height),
    baseAlpha
  });
}
