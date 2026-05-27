import Phaser from "phaser";
import type { Vec2 } from "../../../../../objects/battle/types";
import {
  CRATE_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  getActiveBattleMap,
  isNaturalBattleMapTheme,
  WALL_TEXTURE_KEY,
  WORLD_SIZE,
  type ArenaObstacle
} from "../../constants";
import type { CollisionShape, CollisionShapeSpec, MapBuildingDefinition, MapObstacleDefinition } from "../../maps/battleMapCatalog";
import { createArenaPresentationLayers } from "./arenaBackgroundPresenter";
import { createPickupPads } from "./arenaDecorationPresenter";
import { createStaticObstacleMetalSkin } from "./obstacleSkinPresenter";

export type OccludableSprite = Phaser.GameObjects.Image | Phaser.Physics.Arcade.Image;

export type OccludableTrigger =
  | { kind: "aabb"; position: Vec2; size: Vec2 }
  | { kind: "circle"; position: Vec2; radius: number };

export type OccludableMode = "local-probe" | "tree-leaves" | "building-roof";

export interface ObstacleBounds {
  position: Vec2;
  size: Vec2;
  shape?: CollisionShape;
}

export interface OccludableView {
  sprite: OccludableSprite;
  bounds: Phaser.Geom.Rectangle;
  baseAlpha: number;
  mode: OccludableMode;
  trigger?: OccludableTrigger;
  fadeAlpha?: number;
}

export interface ArenaBuilderContext {
  scene: Phaser.Scene;
  wallBodies: Phaser.Physics.Arcade.StaticGroup;
  obstacleBounds: ObstacleBounds[];
  occludables: OccludableView[];
}

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
}

function createBuildings({ scene, wallBodies, obstacleBounds, occludables }: ArenaBuilderContext): void {
  getActiveBattleMap().buildings.forEach((building) => {
    const floor = scene.add
      .image(building.position.x, building.position.y, building.floorTexture)
      .setDisplaySize(building.floorSize.x, building.floorSize.y)
      .setDepth(18)
      .setAlpha(0.98);

    if (building.kind === "hay_shed") {
      floor.setTint(0x9d8454).setAlpha(0.78);
    }

    building.walls.forEach((wall) => {
      const collision = wall.collision;
      if (collision.kind !== "aabb") {
        return;
      }

      const position = collision.position ?? building.position;
      scene.add
        .rectangle(position.x + 5, position.y + 7, collision.size.x + 12, collision.size.y + 12, 0x1b130c, 0.22)
        .setDepth(46);
      scene.add
        .rectangle(position.x, position.y, collision.size.x, collision.size.y, 0x5a3921, 0.88)
        .setStrokeStyle(2, 0x2d1d12, 0.84)
        .setDepth(47);
    });

    building.doors.forEach((door) => {
      scene.add
        .rectangle(door.position.x, door.position.y, door.size.x, door.size.y, 0x2f2117, 0.34)
        .setStrokeStyle(2, 0xd0a15b, 0.32)
        .setDepth(48);
      scene.add
        .image(door.position.x, door.position.y, doorTextureForTheme(getActiveBattleMap().themeId))
        .setDisplaySize(door.size.x, door.size.y)
        .setDepth(49)
        .setAlpha(0.72);
    });

    building.walls.forEach((wall) => {
      const obstacle = buildingWallObstacle(building, wall);
      createStaticObstacle(scene, wallBodies, obstacleBounds, occludables, obstacle, { visible: false });
    });

    const roofPosition = {
      x: building.position.x + building.roofOffset.x,
      y: building.position.y + building.roofOffset.y
    };
    const roof = scene.add
      .image(roofPosition.x, roofPosition.y, building.roofTexture)
      .setDisplaySize(building.roofSize.x, building.roofSize.y)
      .setDepth(72)
      .setAlpha(1);

    registerOccludable(roof, 1, occludables, {
      mode: "building-roof",
      trigger: triggerFromCollision(building.position, building.interior),
      fadeAlpha: 0
    });
  });
}

function createDecorativeObstacles({ scene, wallBodies, obstacleBounds, occludables }: ArenaBuilderContext): void {
  getActiveBattleMap().decorativeObstacles.forEach((obstacle) => {
    scene.add
      .ellipse(obstacle.position.x + 8, obstacle.position.y + 10, obstacle.displaySize.x * 0.72, obstacle.displaySize.y * 0.42, 0x1b2418, 0.2)
      .setDepth(35);

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
            displaySize: obstacle.displaySize
          },
          { visible: true }
        )
      : scene.add
          .image(obstacle.position.x, obstacle.position.y, obstacle.texture)
          .setDisplaySize(obstacle.displaySize.x, obstacle.displaySize.y)
          .setDepth(obstacle.kind === "leaf_pile" ? 24 : 43)
          .setAlpha(obstacle.kind === "leaf_pile" ? 0.82 : 0.94);

    if (obstacle.kind === "bush") {
      sprite.setDepth(44).setAlpha(0.92);
    }
  });
}

function createTrees({ scene, wallBodies, obstacleBounds, occludables }: ArenaBuilderContext): void {
  getActiveBattleMap().trees.forEach((tree) => {
    scene.add
      .ellipse(tree.position.x + 8, tree.position.y + 12, tree.trunkSize.x * 0.58, tree.trunkSize.y * 0.36, 0x10170e, 0.28)
      .setDepth(42);

    createStaticObstacle(
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
    ).setDepth(50);

    const leavesPosition = {
      x: tree.position.x + tree.leavesOffset.x,
      y: tree.position.y + tree.leavesOffset.y
    };
    const leaves = scene.add
      .image(leavesPosition.x, leavesPosition.y, tree.leavesTexture)
      .setDisplaySize(tree.leavesSize.x, tree.leavesSize.y)
      .setDepth(64)
      .setAlpha(1);

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
  options: { visible: boolean }
): Phaser.Physics.Arcade.Image {
  const textureKey = obstacle.texture ?? (obstacle.kind === "wall" ? WALL_TEXTURE_KEY : CRATE_TEXTURE_KEY);
  const displaySize = obstacle.displaySize ?? obstacle.size;
  const staticImage = scene.physics.add
    .staticImage(obstacle.position.x, obstacle.position.y, textureKey)
    .setDisplaySize(displaySize.x, displaySize.y)
    .setDepth(depthForObstacle(obstacle));

  if (!isNaturalBattleMapTheme(getActiveBattleMap().themeId)) {
    createStaticObstacleMetalSkin(scene, obstacle, staticImage.depth);
    staticImage
      .setTint(obstacle.kind === "wall" ? 0x243039 : 0x2d3437)
      .setAlpha(obstacle.kind === "wall" ? 0.98 : 0.96);
  } else if (!options.visible) {
    staticImage.setVisible(false).setAlpha(0.01);
  }

  staticImage.refreshBody();
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

function buildingWallObstacle(building: MapBuildingDefinition, wall: MapBuildingDefinition["walls"][number]): ArenaObstacle {
  const shape = shapeFromSpec(wall.collision);
  const position = wall.collision.position ?? building.position;
  return {
    obstacleId: `${building.buildingId}-${wall.wallId}`,
    kind: "building-wall",
    position: { x: position.x, y: position.y },
    size: collisionBoundsSize(wall.collision),
    shape
  };
}

function shapeFromSpec(collision: CollisionShapeSpec): CollisionShape {
  if (collision.kind === "circle") {
    return { kind: "circle", radius: collision.radius };
  }

  return { kind: "aabb", size: { x: collision.size.x, y: collision.size.y } };
}

function collisionBoundsSize(collision: CollisionShapeSpec): Vec2 {
  if (collision.kind === "circle") {
    return { x: collision.radius * 2, y: collision.radius * 2 };
  }

  return { x: collision.size.x, y: collision.size.y };
}

function triggerFromCollision(fallbackPosition: Vec2, collision: CollisionShapeSpec): OccludableTrigger {
  const position = collision.position ?? fallbackPosition;
  if (collision.kind === "circle") {
    return { kind: "circle", position: { x: position.x, y: position.y }, radius: collision.radius };
  }

  return {
    kind: "aabb",
    position: { x: position.x, y: position.y },
    size: { x: collision.size.x, y: collision.size.y }
  };
}

function mapDecorativeKind(kind: MapObstacleDefinition["kind"]): ArenaObstacle["kind"] {
  switch (kind) {
    case "rock":
      return "rock";
    case "logs":
      return "logs";
    case "hay":
      return "hay";
    case "stump":
      return "stump";
    case "bush":
    case "leaf_pile":
      return "crate";
  }
}

function doorTextureForTheme(themeId: ReturnType<typeof getActiveBattleMap>["themeId"]): string {
  return themeId === "fall" ? "fall-door" : "shared-door";
}

function backgroundColorForTheme(themeId: ReturnType<typeof getActiveBattleMap>["themeId"]): string {
  switch (themeId) {
    case "fall":
      return "#243526";
    case "winter":
      return "#c7dce5";
    case "normal":
      return "#1f351f";
    case "industrial":
      return "#0d0f0f";
  }
}

function depthForObstacle(obstacle: ArenaObstacle): number {
  switch (obstacle.kind) {
    case "wall":
    case "building-wall":
      return 54;
    case "tree-trunk":
      return 49;
    case "rock":
    case "logs":
    case "hay":
    case "stump":
      return 47;
    case "crate":
      return 46;
  }
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
