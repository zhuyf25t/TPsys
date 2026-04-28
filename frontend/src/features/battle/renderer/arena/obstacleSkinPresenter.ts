import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
import type { ArenaObstacle } from "../../../../game/constants";

const OBSTACLE_SKIN_ENERGY_COLOR = 0x58d6ff;
const OBSTACLE_SKIN_GOLD_COLOR = 0xd99a34;
const OBSTACLE_SKIN_SHADOW_COLOR = 0x020405;
const OBSTACLE_SKIN_FOOTPRINT_STROKE = 0x52656b;
const OBSTACLE_SKIN_HIGHLIGHT_COLOR = 0xe8f8ff;
const WALL_BRACE_COLOR = 0x0c151a;
const CRATE_BRACE_COLOR = 0x151819;

export function createStaticObstacleMetalSkin(scene: Phaser.Scene, obstacle: ArenaObstacle, imageDepth: number): void {
  const { position, size } = obstacle;
  const isWall = obstacle.kind === "wall";
  const shadowAlpha = isWall ? 0.34 : 0.26;
  const rimColor = isWall ? OBSTACLE_SKIN_ENERGY_COLOR : OBSTACLE_SKIN_GOLD_COLOR;
  const braceColor = isWall ? WALL_BRACE_COLOR : CRATE_BRACE_COLOR;
  const edgeInset = 7;
  const edgeWidth = Math.max(size.x - 20, 24);
  const edgeHeight = Math.max(size.y - 20, 24);
  const cornerSize = isWall ? 12 : 10;
  const topAlpha = isWall ? 0.3 : 0.26;
  const bottomAlpha = isWall ? 0.42 : 0.36;
  const sideAlpha = isWall ? 0.3 : 0.24;

  if (!isBorderObstacle(obstacle)) {
    createCoverFootprintCues(scene, obstacle, imageDepth);
  }

  scene.add
    .rectangle(position.x + 7, position.y + 9, size.x + 10, size.y + 10, OBSTACLE_SKIN_SHADOW_COLOR, shadowAlpha)
    .setDepth(imageDepth - 2);
  scene.add
    .rectangle(position.x, position.y, size.x - 8, size.y - 8, 0x000000, 0)
    .setStrokeStyle(2, rimColor, isWall ? 0.22 : 0.3)
    .setDepth(imageDepth + 1);

  scene.add.rectangle(position.x, position.y - size.y / 2 + edgeInset, edgeWidth, 5, rimColor, topAlpha).setDepth(imageDepth + 2);
  scene.add.rectangle(position.x, position.y + size.y / 2 - edgeInset, edgeWidth, 6, rimColor, bottomAlpha).setDepth(imageDepth + 2);
  scene.add.rectangle(position.x - size.x / 2 + edgeInset, position.y, 5, edgeHeight, braceColor, sideAlpha).setDepth(imageDepth + 2);
  scene.add.rectangle(position.x + size.x / 2 - edgeInset, position.y, 5, edgeHeight, braceColor, sideAlpha).setDepth(imageDepth + 2);

  createObstacleCornerPlates(scene, position, size, cornerSize, rimColor, imageDepth + 3, isWall ? 0.36 : 0.3);

  if (isWall) {
    scene.add.rectangle(position.x, position.y + size.y * 0.22, size.x - 16, 6, braceColor, 0.42).setDepth(imageDepth + 2);
    return;
  }

  scene.add.rectangle(position.x, position.y, Math.max(size.x - 18, 22), 4, braceColor, 0.26).setDepth(imageDepth + 2);
}

function createObstacleCornerPlates(
  scene: Phaser.Scene,
  position: Vec2,
  size: Vec2,
  cornerSize: number,
  color: number,
  depth: number,
  alpha: number
): void {
  const left = position.x - size.x / 2 + cornerSize / 2 + 4;
  const right = position.x + size.x / 2 - cornerSize / 2 - 4;
  const top = position.y - size.y / 2 + cornerSize / 2 + 4;
  const bottom = position.y + size.y / 2 - cornerSize / 2 - 4;

  scene.add.rectangle(left, top, cornerSize, cornerSize, color, alpha).setDepth(depth);
  scene.add.rectangle(right, top, cornerSize, cornerSize, color, alpha).setDepth(depth);
  scene.add.rectangle(left, bottom, cornerSize, cornerSize, color, alpha).setDepth(depth);
  scene.add.rectangle(right, bottom, cornerSize, cornerSize, color, alpha).setDepth(depth);
}

function createCoverFootprintCues(scene: Phaser.Scene, obstacle: ArenaObstacle, imageDepth: number): void {
  const { position, size } = obstacle;
  const isWall = obstacle.kind === "wall";
  const accentColor = isWall ? OBSTACLE_SKIN_ENERGY_COLOR : OBSTACLE_SKIN_GOLD_COLOR;
  const footprintWidth = size.x + (isWall ? 34 : 26);
  const footprintHeight = size.y + (isWall ? 28 : 22);
  const shadowAlpha = isWall ? 0.2 : 0.16;

  scene.add.rectangle(position.x + 5, position.y + 7, footprintWidth, footprintHeight, OBSTACLE_SKIN_SHADOW_COLOR, shadowAlpha).setDepth(imageDepth - 4);
  scene.add
    .rectangle(position.x, position.y, footprintWidth - 10, footprintHeight - 10, 0x000000, 0)
    .setStrokeStyle(2, OBSTACLE_SKIN_FOOTPRINT_STROKE, isWall ? 0.14 : 0.1)
    .setDepth(imageDepth - 3);

  scene.add.rectangle(position.x, position.y - size.y / 2 - 5, Math.max(size.x - 18, 24), 3, accentColor, isWall ? 0.18 : 0.2).setDepth(imageDepth - 1);
  scene.add.rectangle(position.x, position.y + size.y / 2 + 5, Math.max(size.x - 18, 24), 4, accentColor, isWall ? 0.24 : 0.22).setDepth(imageDepth - 1);
  scene.add.rectangle(position.x - size.x / 2 - 5, position.y, 3, Math.max(size.y - 18, 24), OBSTACLE_SKIN_HIGHLIGHT_COLOR, isWall ? 0.07 : 0.05).setDepth(imageDepth - 1);
  scene.add.rectangle(position.x + size.x / 2 + 5, position.y, 3, Math.max(size.y - 18, 24), accentColor, isWall ? 0.1 : 0.14).setDepth(imageDepth - 1);
}

function isBorderObstacle(obstacle: ArenaObstacle): boolean {
  return obstacle.obstacleId.startsWith("border-");
}
