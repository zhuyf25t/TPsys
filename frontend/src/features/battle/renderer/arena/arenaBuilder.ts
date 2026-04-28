import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
import {
  CRATE_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  INNER_OBSTACLES,
  WALL_TEXTURE_KEY,
  WORLD_SIZE,
  type ArenaObstacle
} from "../../../../game/constants";
import { createArenaPresentationLayers } from "./arenaBackgroundPresenter";
import { createArenaDecorations, createPickupPads } from "./arenaDecorationPresenter";
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

export function buildArena(context: ArenaBuilderContext): void {
  const { scene } = context;

  scene.cameras.main.setBackgroundColor("#0d0f0f");

  createArenaPresentationLayers(scene);
  createPickupPads(scene);
  createArenaDecorations(scene, context.occludables);
  createBorderWalls(scene, context.wallBodies, context.obstacleBounds, context.occludables);
  createInnerStructures(scene, context.wallBodies, context.obstacleBounds, context.occludables);
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
