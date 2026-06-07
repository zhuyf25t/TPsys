import Phaser from "phaser";
import {
  CRATE_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  getActiveBattleMap,
  isNaturalBattleMapTheme,
  WALL_TEXTURE_KEY,
  WORLD_SIZE,
  type ArenaObstacle
} from "../../objects/BattleGameConstants";
import type {
  ArenaBuilderContext,
  ObstacleBounds,
  OccludableSprite,
  OccludableView,
  StaticMapView,
  StaticMapViewSprite
} from "./objects/ArenaBuilderObjects";
import {
  backgroundColorForTheme,
  buildingWallObstacle,
  collisionBoundsSize,
  depthForObstacle,
  doorTextureForTheme,
  mapDecorativeKind,
  shapeFromSpec,
  triggerFromCollision
} from "./functions/ArenaBuilderRules";
import { createArenaPresentationLayers } from "./arenaBackgroundPresenter";
import { createPickupPads, createWinterZombieSetPieces } from "./arenaDecorationPresenter";
import { createStaticObstacleMetalSkin } from "./obstacleSkinPresenter";

/** 涓枃鍚嶏細鏋勫缓绔炴妧鍦猴紙buildArena锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷淬€?*/
export function buildArena(context: ArenaBuilderContext): void {
  const { scene } = context;
  const battleMap = getActiveBattleMap();

  scene.cameras.main.setBackgroundColor(backgroundColorForTheme(battleMap.themeId));

  createArenaPresentationLayers(scene);
  createPickupPads(scene);
  createBorderWalls(scene, context.wallBodies, context.obstacleBounds, context.occludables);

  if (isNaturalBattleMapTheme(battleMap.themeId)) {
    createNaturalMapObjects(context);
    return;
  }

  createIndustrialInnerStructures(scene, context.wallBodies, context.obstacleBounds, context.occludables);
}

function createBorderWalls(
  scene: Phaser.Scene,
  wallBodies: Phaser.Physics.Arcade.StaticGroup,
  obstacleBounds: ObstacleBounds[],
  occludables: OccludableView[]
): void {
  for (let x = FLOOR_TILE_SIZE / 2; x < WORLD_SIZE.x; x += FLOOR_TILE_SIZE) {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-top-${x}`,
      kind: "wall",
      position: { x, y: FLOOR_TILE_SIZE / 2 },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE },
      shape: { kind: "aabb", size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } }
    }, { visible: !isNaturalBattleMapTheme(getActiveBattleMap().themeId) });
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-bottom-${x}`,
      kind: "wall",
      position: { x, y: WORLD_SIZE.y - FLOOR_TILE_SIZE / 2 },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE },
      shape: { kind: "aabb", size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } }
    }, { visible: !isNaturalBattleMapTheme(getActiveBattleMap().themeId) });
  }

  for (let y = FLOOR_TILE_SIZE * 1.5; y < WORLD_SIZE.y - FLOOR_TILE_SIZE / 2; y += FLOOR_TILE_SIZE) {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-left-${y}`,
      kind: "wall",
      position: { x: FLOOR_TILE_SIZE / 2, y },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE },
      shape: { kind: "aabb", size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } }
    }, { visible: !isNaturalBattleMapTheme(getActiveBattleMap().themeId) });
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, {
      obstacleId: `border-right-${y}`,
      kind: "wall",
      position: { x: WORLD_SIZE.x - FLOOR_TILE_SIZE / 2, y },
      size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE },
      shape: { kind: "aabb", size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } }
    }, { visible: !isNaturalBattleMapTheme(getActiveBattleMap().themeId) });
  }
}

function createIndustrialInnerStructures(
  scene: Phaser.Scene,
  wallBodies: Phaser.Physics.Arcade.StaticGroup,
  obstacleBounds: ObstacleBounds[],
  occludables: OccludableView[]
): void {
  scene.add.rectangle(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1504, 864, 0x10151a, 0.08).setDepth(-14);
  scene.add.rectangle(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, 1320, 680, 0xf7e4ba, 0.02).setDepth(-13);

  getActiveBattleMap().innerObstacles.forEach((obstacle) => {
    createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, obstacle, { visible: true });
  });
}

function createNaturalMapObjects(context: ArenaBuilderContext): void {
  createBuildings(context);
  createDecorativeObstacles(context);
  createTrees(context);
  createWinterZombieSetPieces(context.scene, context.occludables);
}

function createBuildings({ scene, wallBodies, obstacleBounds, occludables, staticMapViews }: ArenaBuilderContext): void {
  getActiveBattleMap().buildings.forEach((building) => {
    const floor = registerStaticMapView(
      scene.add
        .image(building.position.x, building.position.y, building.floorTexture)
        .setDisplaySize(building.floorSize.x, building.floorSize.y)
        .setDepth(18)
        .setAlpha(0.98),
      staticMapViews
    );

    if (building.kind === "hay_shed") {
      floor.setTint(0x9d8454).setAlpha(0.78);
    }

    building.walls.forEach((wall) => {
      const collision = wall.collision;
      if (collision.kind !== "aabb") {
        return;
      }

      const position = collision.position ?? building.position;
      registerStaticMapView(
        scene.add
          .rectangle(position.x + 5, position.y + 7, collision.size.x + 12, collision.size.y + 12, 0x1b130c, 0.22)
          .setDepth(46),
        staticMapViews
      );
      registerStaticMapView(
        scene.add
          .rectangle(position.x, position.y, collision.size.x, collision.size.y, 0x5a3921, 0.88)
          .setStrokeStyle(2, 0x2d1d12, 0.84)
          .setDepth(47),
        staticMapViews
      );
    });

    building.doors.forEach((door) => {
      registerStaticMapView(
        scene.add
          .rectangle(door.position.x, door.position.y, door.size.x, door.size.y, 0x2f2117, 0.34)
          .setStrokeStyle(2, 0xd0a15b, 0.32)
          .setDepth(48),
        staticMapViews
      );
      registerStaticMapView(
        scene.add
          .image(door.position.x, door.position.y, doorTextureForTheme(getActiveBattleMap().themeId))
          .setDisplaySize(door.size.x, door.size.y)
          .setDepth(49)
          .setAlpha(0.72),
        staticMapViews
      );
    });

    building.walls.forEach((wall) => {
      const obstacle = buildingWallObstacle(building, wall);
      createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, obstacle, { visible: false, collisionOnly: true });
    });

    const roofPosition = {
      x: building.position.x + building.roofOffset.x,
      y: building.position.y + building.roofOffset.y
    };
    const roof = registerStaticMapView(
      scene.add
        .image(roofPosition.x, roofPosition.y, building.roofTexture)
        .setDisplaySize(building.roofSize.x, building.roofSize.y)
        .setDepth(72)
        .setAlpha(1),
      staticMapViews
    );

    registerOccludable(roof, 1, occludables, {
      mode: "building-roof",
      trigger: triggerFromCollision(building.position, building.interior),
      fadeAlpha: 0
    });
  });
}

function createDecorativeObstacles({ scene, wallBodies, obstacleBounds, occludables, staticMapViews }: ArenaBuilderContext): void {
  getActiveBattleMap().decorativeObstacles.forEach((obstacle) => {
    registerStaticMapView(
      scene.add
        .ellipse(obstacle.position.x + 8, obstacle.position.y + 10, obstacle.displaySize.x * 0.72, obstacle.displaySize.y * 0.42, 0x1b2418, 0.2)
        .setRotation(obstacle.rotation ?? 0)
        .setDepth(35),
      staticMapViews
    );

    const sprite = obstacle.collision
      ? createStaticObstacle(
          scene,
          wallBodies,
          obstacleBounds,
          occludables,
          {
            obstacleId: obstacle.obstacleId,
            kind: mapDecorativeKind(obstacle.kind),
            position: obstacle.position,
            size: collisionBoundsSize(obstacle.collision),
            shape: shapeFromSpec(obstacle.collision),
            texture: obstacle.texture,
            displaySize: obstacle.displaySize,
            rotation: obstacle.rotation
          },
          { visible: true }
        )
      : scene.add
          .image(obstacle.position.x, obstacle.position.y, obstacle.texture)
          .setDisplaySize(obstacle.displaySize.x, obstacle.displaySize.y)
          .setRotation(obstacle.rotation ?? 0)
          .setDepth(obstacle.kind === "leaf_pile" ? 24 : 43)
          .setAlpha(obstacle.kind === "leaf_pile" ? 0.82 : 0.94);
    registerStaticMapView(sprite, staticMapViews);

    if (obstacle.kind === "bush") {
      sprite.setDepth(44).setAlpha(0.92);
    }
  });
}

function createTrees({ scene, wallBodies, obstacleBounds, occludables, staticMapViews }: ArenaBuilderContext): void {
  getActiveBattleMap().trees.forEach((tree) => {
    registerStaticMapView(
      scene.add
        .ellipse(tree.position.x + 8, tree.position.y + 12, tree.trunkSize.x * 0.58, tree.trunkSize.y * 0.36, 0x10170e, 0.28)
        .setDepth(42),
      staticMapViews
    );

    registerStaticMapView(createStaticObstacle(
      scene,
      wallBodies,
      obstacleBounds,
      occludables,
      {
        obstacleId: `${tree.treeId}-trunk`,
        kind: "tree-trunk",
        position: tree.position,
        size: collisionBoundsSize(tree.trunkCollision),
        shape: shapeFromSpec(tree.trunkCollision),
        texture: tree.trunkTexture,
        displaySize: tree.trunkSize
      },
      { visible: true }
    ).setDepth(50), staticMapViews);

    const leavesPosition = {
      x: tree.position.x + tree.leavesOffset.x,
      y: tree.position.y + tree.leavesOffset.y
    };
    const leaves = registerStaticMapView(
      scene.add
        .image(leavesPosition.x, leavesPosition.y, tree.leavesTexture)
        .setDisplaySize(tree.leavesSize.x, tree.leavesSize.y)
        .setDepth(64)
        .setAlpha(1),
      staticMapViews
    );

    registerOccludable(leaves, 1, occludables, {
      mode: "tree-leaves",
      trigger: triggerFromCollision(tree.position, tree.leafOcclusion),
      fadeAlpha: 0.4
    });
  });
}

function createStaticObstacle(
  scene: Phaser.Scene,
  wallBodies: Phaser.Physics.Arcade.StaticGroup,
  obstacleBounds: ObstacleBounds[],
  occludables: OccludableView[],
  obstacle: ArenaObstacle,
  options: { visible: boolean; collisionOnly?: boolean }
): Phaser.Physics.Arcade.Image {
  const textureKey = obstacle.texture ?? (obstacle.kind === "wall" ? WALL_TEXTURE_KEY : CRATE_TEXTURE_KEY);
  const displaySize = obstacle.displaySize ?? obstacle.size;
  const staticImage = scene.physics.add
    .staticImage(obstacle.position.x, obstacle.position.y, textureKey)
    .setDisplaySize(displaySize.x, displaySize.y)
    .setRotation(obstacle.rotation ?? 0)
    .setDepth(depthForObstacle(obstacle));

  if (!isNaturalBattleMapTheme(getActiveBattleMap().themeId)) {
    createStaticObstacleMetalSkin(scene, obstacle, staticImage.depth);
    staticImage
      .setTint(obstacle.kind === "wall" ? 0x243039 : 0x2d3437)
      .setAlpha(obstacle.kind === "wall" ? 0.98 : 0.96);
  } else if (options.collisionOnly) {
    staticImage.setAlpha(0);
  } else if (!options.visible) {
    staticImage.setVisible(false).setAlpha(0.01);
  }

  staticImage.refreshBody();
  const body = staticImage.body as Phaser.Physics.Arcade.StaticBody | null;
  if (body) {
    body.setSize(obstacle.size.x, obstacle.size.y, true);
    body.enable = true;
  }
  wallBodies.add(staticImage);
  obstacleBounds.push({
    position: { x: obstacle.position.x, y: obstacle.position.y },
    size: { x: obstacle.size.x, y: obstacle.size.y },
    shape: obstacle.shape
  });

  if (!isNaturalBattleMapTheme(getActiveBattleMap().themeId) && obstacle.kind === "wall") {
    registerOccludable(staticImage, 1, occludables, { mode: "local-probe" });
  }

  return staticImage;
}

function registerOccludable(
  sprite: OccludableSprite,
  baseAlpha: number,
  occludables: OccludableView[],
  options: Pick<OccludableView, "mode" | "trigger" | "fadeAlpha">
): void {
  const bounds = sprite.getBounds();
  occludables.push({
    sprite,
    bounds: new Phaser.Geom.Rectangle(bounds.x, bounds.y, bounds.width, bounds.height),
    baseAlpha,
    mode: options.mode,
    ...(options.trigger ? { trigger: options.trigger } : {}),
    ...(options.fadeAlpha !== undefined ? { fadeAlpha: options.fadeAlpha } : {})
  });
}

function registerStaticMapView<T extends StaticMapViewSprite>(sprite: T, staticMapViews: StaticMapView[]): T {
  const bounds = sprite.getBounds();
  staticMapViews.push({
    sprite,
    bounds: new Phaser.Geom.Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
  });
  return sprite;
}
