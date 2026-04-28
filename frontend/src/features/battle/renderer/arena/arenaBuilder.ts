import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
import {
  CRATE_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  INNER_OBSTACLES,
  ROCK_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WALL_TEXTURE_KEY,
  WORLD_SIZE,
  type ArenaObstacle
} from "../../../../game/constants";
import { ITEM_PICKUP_SPAWN_POINTS, WEAPON_PICKUP_SPAWN_POINTS } from "../../../../game/spawn";
import { createArenaPresentationLayers } from "./arenaBackgroundPresenter";
import { createStaticObstacleMetalSkin } from "./obstacleSkinPresenter";

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

const ARENA_ENERGY_ACCENT_COLOR = 0x58d6ff;

export function buildArena(context: ArenaBuilderContext): void {
  const { scene } = context;

  scene.cameras.main.setBackgroundColor("#0d0f0f");

  createArenaPresentationLayers(scene);
  createPickupPads(scene);
  createArenaDecorations(scene, context.occludables);
  createBorderWalls(scene, context.wallBodies, context.obstacleBounds, context.occludables);
  createInnerStructures(scene, context.wallBodies, context.obstacleBounds, context.occludables);
}

function createPickupPads(scene: Phaser.Scene): void {
  WEAPON_PICKUP_SPAWN_POINTS.forEach((point) => {
    scene.add.tileSprite(point.position.x, point.position.y + 8, 112, 82, STONE_TRIM_TEXTURE_KEY).setDepth(-12).setTint(0x29343a).setAlpha(0.98);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 118, 88, 0x11181c, 0.18)
      .setStrokeStyle(2, 0xd99a34, 0.72)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 86, 50, 0xf0bd58, 0.06)
      .setStrokeStyle(1, 0xf0bd58, 0.46)
      .setDepth(-10);
  });

  ITEM_PICKUP_SPAWN_POINTS.forEach((point) => {
    scene.add
      .tileSprite(point.position.x, point.position.y + 8, 100, 76, STONE_TRIM_TEXTURE_KEY)
      .setDepth(-12)
      .setTint(0x21343c)
      .setAlpha(0.96);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 106, 82, 0x0d1a1e, 0.18)
      .setStrokeStyle(2, ARENA_ENERGY_ACCENT_COLOR, 0.72)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 72, 44, 0x8ff3ff, 0.06)
      .setStrokeStyle(1, 0x8ff3ff, 0.4)
      .setDepth(-10);
  });
}

function createArenaDecorations(scene: Phaser.Scene, occludables: OccludableView[]): void {
  const pylons: readonly Vec2[] = [
    { x: 320, y: 320 },
    { x: 2240, y: 320 },
    { x: 320, y: 1280 },
    { x: 2240, y: 1280 },
    { x: 736, y: 224 },
    { x: 1824, y: 224 },
    { x: 736, y: 1376 },
    { x: 1824, y: 1376 }
  ];

  const machinery: readonly Vec2[] = [
    { x: 896, y: 448 },
    { x: 1664, y: 448 },
    { x: 896, y: 1152 },
    { x: 1664, y: 1152 },
    { x: 640, y: 640 },
    { x: 1920, y: 640 },
    { x: 640, y: 960 },
    { x: 1920, y: 960 }
  ];

  const lowDeckPlates: readonly Vec2[] = [
    { x: 544, y: 576 },
    { x: 2016, y: 576 },
    { x: 544, y: 1024 },
    { x: 2016, y: 1024 },
    { x: 1280, y: 224 },
    { x: 1280, y: 1376 }
  ];

  pylons.forEach((position) => {
    scene.add.rectangle(position.x + 8, position.y + 16, 82, 96, 0x020405, 0.36).setDepth(43);
    const pylon = scene.add.image(position.x, position.y, WALL_TEXTURE_KEY).setScale(1.16).setDepth(54).setTint(0x1b252c).setAlpha(0.96);
    scene.add.rectangle(position.x, position.y + 30, 78, 7, ARENA_ENERGY_ACCENT_COLOR, 0.2).setDepth(55);
    registerOccludable(pylon, 0.96, occludables);
  });

  machinery.forEach((position) => {
    scene.add.rectangle(position.x + 10, position.y + 12, 74, 58, 0x020405, 0.32).setDepth(42);
    const machine = scene.add.image(position.x, position.y, ROCK_TEXTURE_KEY).setScale(1.08).setDepth(53).setTint(0x2b363b).setAlpha(0.92);
    scene.add.rectangle(position.x, position.y - 24, 44, 4, 0xd99a34, 0.22).setDepth(54);
    registerOccludable(machine, 0.92, occludables);
  });

  lowDeckPlates.forEach((position) => {
    scene.add.image(position.x, position.y, CRATE_TEXTURE_KEY).setScale(1.1).setDepth(11).setTint(0x1f2b31).setAlpha(0.78);
    scene.add.rectangle(position.x, position.y, 74, 6, 0x5fd9ff, 0.14).setDepth(12);
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
    .setDisplaySize(obstacle.size.x, obstacle.size.y)
    .setDepth(obstacle.kind === "wall" ? 54 : 44);

  if (obstacle.kind === "crate") {
    staticImage.setDepth(46);
  }

  createStaticObstacleMetalSkin(scene, obstacle, staticImage.depth);
  staticImage
    .setTint(obstacle.kind === "wall" ? 0x243039 : 0x2d3437)
    .setAlpha(obstacle.kind === "wall" ? 0.98 : 0.96);

  staticImage.refreshBody();
  wallBodies.add(staticImage);
  obstacleBounds.push({
    position: { x: obstacle.position.x, y: obstacle.position.y },
    size: { x: obstacle.size.x, y: obstacle.size.y }
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
