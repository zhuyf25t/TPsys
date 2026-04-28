import Phaser from "phaser";
import {
  FLOOR_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  GLOBAL_BACKGROUND_PADDING,
  HERO_RADIUS,
  OUTSIDE_TEXTURE_KEY,
  STONE_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WORLD_SIZE
} from "../../../../game/constants";

const OUT_OF_BOUNDS_SHADOW_DEPTH = -37;
const BORDER_SHADOW_DEPTH = 39;
const BORDER_WARNING_DEPTH = 40;
const BORDER_DANGER_COLOR = 0xd99a34;
const BORDER_ENERGY_COLOR = 0x58d6ff;
const BORDER_SHADOW_COLOR = 0x05070a;

export function createArenaPresentationLayers(scene: Phaser.Scene): void {
  const extendedWidth = WORLD_SIZE.x + GLOBAL_BACKGROUND_PADDING * 2;
  const extendedHeight = WORLD_SIZE.y + GLOBAL_BACKGROUND_PADDING * 2;

  scene.add
    .tileSprite(WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, extendedWidth, extendedHeight, OUTSIDE_TEXTURE_KEY)
    .setDepth(-40)
    .setTint(0x11171b)
    .setAlpha(0.98);

  createPatternRect(scene, WORLD_SIZE.x / 2, WORLD_SIZE.y / 2, extendedWidth - 180, extendedHeight - 180, STONE_TEXTURE_KEY, -39, 0.18, 0x1b2428);
  createPatternRect(scene, 128, 160, 680, 520, STONE_TRIM_TEXTURE_KEY, -38, 0.12, 0x0a1014);
  createPatternRect(scene, WORLD_SIZE.x - 128, 160, 680, 520, STONE_TRIM_TEXTURE_KEY, -38, 0.12, 0x0a1014);
  createPatternRect(scene, 128, WORLD_SIZE.y - 160, 680, 520, STONE_TRIM_TEXTURE_KEY, -38, 0.12, 0x0a1014);
  createPatternRect(scene, WORLD_SIZE.x - 128, WORLD_SIZE.y - 160, 680, 520, STONE_TRIM_TEXTURE_KEY, -38, 0.12, 0x0a1014);
  createOutOfBoundsShadow(scene, extendedWidth, extendedHeight);

  createMetalArenaFloor(scene);
  createBoundaryReadabilityLayer(scene);
}

function createPatternRect(
  scene: Phaser.Scene,
  x: number,
  y: number,
  width: number,
  height: number,
  textureKey: string,
  depth: number,
  alpha: number,
  tint?: number
): void {
  const sprite = scene.add.tileSprite(x, y, width, height, textureKey).setDepth(depth).setAlpha(alpha);

  if (tint !== undefined) {
    sprite.setTint(tint);
  }
}

function createMetalArenaFloor(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;

  scene.add.tileSprite(centerX, centerY, WORLD_SIZE.x, WORLD_SIZE.y, FLOOR_TEXTURE_KEY).setDepth(-20).setTint(0x1a2529);
  scene.add.rectangle(centerX, centerY, WORLD_SIZE.x, WORLD_SIZE.y, 0x020507, 0.12).setDepth(-19);

  createPatternRect(scene, centerX, centerY, WORLD_SIZE.x - 192, WORLD_SIZE.y - 192, STONE_TEXTURE_KEY, -18, 0.3, 0x263239);
  scene.add.rectangle(centerX, centerY, WORLD_SIZE.x - 240, WORLD_SIZE.y - 240, 0x091015, 0.36).setDepth(-17);

  scene.add.rectangle(centerX, centerY, 1544, 936, 0x1b252b, 0.74).setDepth(-16).setStrokeStyle(8, 0x070a0d, 0.78);
  scene.add.rectangle(centerX, centerY, 1392, 784, 0x223038, 0.54).setDepth(-15).setStrokeStyle(5, 0xc08a31, 0.36);
  scene.add.rectangle(centerX, centerY, 1192, 584, 0x0f171c, 0.28).setDepth(-14).setStrokeStyle(3, BORDER_ENERGY_COLOR, 0.28);
  scene.add.rectangle(centerX, centerY, 760, 368, 0x27353b, 0.28).setDepth(-13).setStrokeStyle(2, 0xf0bf54, 0.2);

  createMetalPanelSeams(scene);
  createCentralPanelHierarchy(scene);
  createArenaLightStrips(scene);
  createIndustrialCornerShadows(scene);
}

function createMetalPanelSeams(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const seamColor = 0x05090c;
  const rivetColor = 0x60727a;

  for (let x = FLOOR_TILE_SIZE * 3; x < WORLD_SIZE.x - FLOOR_TILE_SIZE * 3; x += FLOOR_TILE_SIZE * 3) {
    scene.add.rectangle(x, centerY, 2, WORLD_SIZE.y - FLOOR_TILE_SIZE * 4, seamColor, 0.22).setDepth(-12);
  }

  for (let y = FLOOR_TILE_SIZE * 3; y < WORLD_SIZE.y - FLOOR_TILE_SIZE * 3; y += FLOOR_TILE_SIZE * 3) {
    scene.add.rectangle(centerX, y, WORLD_SIZE.x - FLOOR_TILE_SIZE * 4, 2, seamColor, 0.22).setDepth(-12);
  }

  for (let x = FLOOR_TILE_SIZE * 4; x < WORLD_SIZE.x - FLOOR_TILE_SIZE * 4; x += FLOOR_TILE_SIZE * 6) {
    scene.add.rectangle(x, centerY - 304, 8, 8, rivetColor, 0.24).setDepth(-11);
    scene.add.rectangle(x, centerY + 304, 8, 8, rivetColor, 0.24).setDepth(-11);
  }

  for (let y = FLOOR_TILE_SIZE * 4; y < WORLD_SIZE.y - FLOOR_TILE_SIZE * 4; y += FLOOR_TILE_SIZE * 5) {
    scene.add.rectangle(centerX - 544, y, 8, 8, rivetColor, 0.18).setDepth(-11);
    scene.add.rectangle(centerX + 544, y, 8, 8, rivetColor, 0.18).setDepth(-11);
  }
}

function createCentralPanelHierarchy(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const gold = 0xd99a34;
  const cyan = BORDER_ENERGY_COLOR;

  scene.add.rectangle(centerX, centerY, 1072, 328, 0x18242a, 0.16).setDepth(-12).setStrokeStyle(2, 0x41535a, 0.16);
  scene.add.rectangle(centerX, centerY, 820, 220, 0x0a1217, 0.14).setDepth(-11).setStrokeStyle(2, gold, 0.18);

  scene.add.rectangle(centerX, centerY - 164, 840, 3, cyan, 0.18).setDepth(-9);
  scene.add.rectangle(centerX, centerY + 164, 840, 3, cyan, 0.18).setDepth(-9);
  scene.add.rectangle(centerX - 536, centerY, 3, 300, gold, 0.16).setDepth(-9);
  scene.add.rectangle(centerX + 536, centerY, 3, 300, gold, 0.16).setDepth(-9);

  scene.add.rectangle(centerX - 224, centerY, 124, 4, gold, 0.22).setDepth(-8);
  scene.add.rectangle(centerX + 224, centerY, 124, 4, gold, 0.22).setDepth(-8);
  scene.add.rectangle(centerX, centerY - 88, 4, 82, cyan, 0.18).setDepth(-8);
  scene.add.rectangle(centerX, centerY + 88, 4, 82, cyan, 0.18).setDepth(-8);
}

function createArenaLightStrips(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const gold = 0xd99a34;
  const cyan = BORDER_ENERGY_COLOR;

  scene.add.rectangle(centerX, centerY - 420, 1024, 5, gold, 0.28).setDepth(-10);
  scene.add.rectangle(centerX, centerY + 420, 1024, 5, gold, 0.34).setDepth(-10);
  scene.add.rectangle(centerX - 680, centerY, 5, 560, cyan, 0.16).setDepth(-10);
  scene.add.rectangle(centerX + 680, centerY, 5, 560, cyan, 0.22).setDepth(-10);

  scene.add.rectangle(centerX, centerY - 292, 560, 3, cyan, 0.22).setDepth(-9);
  scene.add.rectangle(centerX, centerY + 292, 560, 3, cyan, 0.2).setDepth(-9);
  scene.add.rectangle(centerX - 424, centerY, 3, 280, gold, 0.2).setDepth(-9);
  scene.add.rectangle(centerX + 424, centerY, 3, 280, gold, 0.2).setDepth(-9);
}

function createIndustrialCornerShadows(scene: Phaser.Scene): void {
  const cornerColor = 0x020405;
  const accentColor = 0x23333a;

  scene.add.rectangle(224, 224, 448, 448, cornerColor, 0.3).setDepth(-13);
  scene.add.rectangle(WORLD_SIZE.x - 224, 224, 448, 448, cornerColor, 0.3).setDepth(-13);
  scene.add.rectangle(224, WORLD_SIZE.y - 224, 448, 448, cornerColor, 0.38).setDepth(-13);
  scene.add.rectangle(WORLD_SIZE.x - 224, WORLD_SIZE.y - 224, 448, 448, cornerColor, 0.38).setDepth(-13);

  scene.add.rectangle(368, 368, 232, 18, accentColor, 0.22).setDepth(-12);
  scene.add.rectangle(WORLD_SIZE.x - 368, 368, 232, 18, accentColor, 0.22).setDepth(-12);
  scene.add.rectangle(368, WORLD_SIZE.y - 368, 232, 18, accentColor, 0.26).setDepth(-12);
  scene.add.rectangle(WORLD_SIZE.x - 368, WORLD_SIZE.y - 368, 232, 18, accentColor, 0.26).setDepth(-12);
}

function createOutOfBoundsShadow(scene: Phaser.Scene, extendedWidth: number, extendedHeight: number): void {
  const padding = GLOBAL_BACKGROUND_PADDING;
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const farHazeAlpha = 0.08;
  const strongFarHazeAlpha = 0.12;

  scene.add.rectangle(centerX, -padding / 2, extendedWidth, padding, 0x020304, farHazeAlpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
  scene.add.rectangle(centerX, WORLD_SIZE.y + padding / 2, extendedWidth, padding, 0x020304, strongFarHazeAlpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
  scene.add.rectangle(-padding / 2, centerY, padding, extendedHeight, 0x020304, farHazeAlpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
  scene.add.rectangle(WORLD_SIZE.x + padding / 2, centerY, padding, extendedHeight, 0x020304, strongFarHazeAlpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);

  createOutOfBoundsEdgeBand(scene, "top", 24, 48, 0.18);
  createOutOfBoundsEdgeBand(scene, "top", 84, 44, 0.11);
  createOutOfBoundsEdgeBand(scene, "bottom", 24, 56, 0.28);
  createOutOfBoundsEdgeBand(scene, "bottom", 92, 52, 0.15);
  createOutOfBoundsEdgeBand(scene, "left", 24, 48, 0.18);
  createOutOfBoundsEdgeBand(scene, "left", 84, 44, 0.11);
  createOutOfBoundsEdgeBand(scene, "right", 24, 56, 0.28);
  createOutOfBoundsEdgeBand(scene, "right", 92, 52, 0.15);
  createOutOfBoundsRailCues(scene);
}

function createOutOfBoundsEdgeBand(
  scene: Phaser.Scene,
  edge: "top" | "bottom" | "left" | "right",
  offset: number,
  thickness: number,
  alpha: number
): void {
  const horizontalWidth = WORLD_SIZE.x + FLOOR_TILE_SIZE * 2;
  const verticalHeight = WORLD_SIZE.y + FLOOR_TILE_SIZE * 2;
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;

  if (edge === "top") {
    scene.add.rectangle(centerX, -offset, horizontalWidth, thickness, 0x020304, alpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
    return;
  }

  if (edge === "bottom") {
    scene.add.rectangle(centerX, WORLD_SIZE.y + offset, horizontalWidth, thickness, 0x020304, alpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
    return;
  }

  if (edge === "left") {
    scene.add.rectangle(-offset, centerY, thickness, verticalHeight, 0x020304, alpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
    return;
  }

  scene.add.rectangle(WORLD_SIZE.x + offset, centerY, thickness, verticalHeight, 0x020304, alpha).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH);
}

function createOutOfBoundsRailCues(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const railColor = 0x0b1216;
  const glintColor = 0xc58f39;

  scene.add.rectangle(centerX, -6, WORLD_SIZE.x + FLOOR_TILE_SIZE, 8, railColor, 0.72).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 1);
  scene.add.rectangle(centerX, WORLD_SIZE.y + 6, WORLD_SIZE.x + FLOOR_TILE_SIZE, 10, railColor, 0.86).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 1);
  scene.add.rectangle(-6, centerY, 8, WORLD_SIZE.y + FLOOR_TILE_SIZE, railColor, 0.72).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 1);
  scene.add.rectangle(WORLD_SIZE.x + 6, centerY, 10, WORLD_SIZE.y + FLOOR_TILE_SIZE, railColor, 0.86).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 1);

  for (let x = FLOOR_TILE_SIZE; x < WORLD_SIZE.x; x += FLOOR_TILE_SIZE * 3) {
    scene.add.rectangle(x, -12, 34, 5, glintColor, 0.2).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 2);
    scene.add.rectangle(x, WORLD_SIZE.y + 12, 42, 6, glintColor, 0.36).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 2);
  }

  for (let y = FLOOR_TILE_SIZE; y < WORLD_SIZE.y; y += FLOOR_TILE_SIZE * 3) {
    scene.add.rectangle(-12, y, 5, 34, glintColor, 0.2).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 2);
    scene.add.rectangle(WORLD_SIZE.x + 12, y, 6, 42, glintColor, 0.36).setDepth(OUT_OF_BOUNDS_SHADOW_DEPTH + 2);
  }
}

// Visual-only cues for the collision border; obstacleBounds remain unchanged.
function createBoundaryReadabilityLayer(scene: Phaser.Scene): void {
  const halfTile = FLOOR_TILE_SIZE / 2;
  const playableWidth = WORLD_SIZE.x - FLOOR_TILE_SIZE * 2;
  const playableHeight = WORLD_SIZE.y - FLOOR_TILE_SIZE * 2;
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const innerLeft = FLOOR_TILE_SIZE;
  const innerRight = WORLD_SIZE.x - FLOOR_TILE_SIZE;
  const innerTop = FLOOR_TILE_SIZE;
  const innerBottom = WORLD_SIZE.y - FLOOR_TILE_SIZE;

  createBorderShadow(scene, centerX, halfTile, WORLD_SIZE.x, FLOOR_TILE_SIZE, 0.18);
  createBorderShadow(scene, centerX, WORLD_SIZE.y - halfTile, WORLD_SIZE.x, FLOOR_TILE_SIZE, 0.28);
  createBorderShadow(scene, halfTile, centerY, FLOOR_TILE_SIZE, WORLD_SIZE.y, 0.18);
  createBorderShadow(scene, WORLD_SIZE.x - halfTile, centerY, FLOOR_TILE_SIZE, WORLD_SIZE.y, 0.28);

  scene.add
    .rectangle(centerX, centerY, playableWidth, playableHeight, 0x000000, 0)
    .setStrokeStyle(6, 0x030608, 0.62)
    .setDepth(BORDER_SHADOW_DEPTH);

  createBoundaryWarningLine(scene, centerX, innerTop + 8, playableWidth, 5, 0.22);
  createBoundaryWarningLine(scene, centerX, innerBottom - 8, playableWidth, 6, 0.38);
  createBoundaryWarningLine(scene, innerLeft + 8, centerY, 5, playableHeight, 0.22);
  createBoundaryWarningLine(scene, innerRight - 8, centerY, 6, playableHeight, 0.38);
  createBoundaryEnergyLine(scene, centerX, innerTop + 18, playableWidth - 96, 2, 0.14);
  createBoundaryEnergyLine(scene, centerX, innerBottom - 18, playableWidth - 96, 2, 0.22);
  createBoundaryEnergyLine(scene, innerLeft + 18, centerY, 2, playableHeight - 96, 0.14);
  createBoundaryEnergyLine(scene, innerRight - 18, centerY, 2, playableHeight - 96, 0.22);
  createHeroCenterLimitCues(scene);
  createBoundaryWarningTicks(scene);
}

function createBorderShadow(scene: Phaser.Scene, x: number, y: number, width: number, height: number, alpha: number): void {
  scene.add.rectangle(x, y, width, height, BORDER_SHADOW_COLOR, alpha).setDepth(BORDER_SHADOW_DEPTH);
}

function createBoundaryWarningLine(scene: Phaser.Scene, x: number, y: number, width: number, height: number, alpha: number): void {
  scene.add.rectangle(x, y, width, height, BORDER_DANGER_COLOR, alpha).setDepth(BORDER_WARNING_DEPTH);
}

function createBoundaryEnergyLine(scene: Phaser.Scene, x: number, y: number, width: number, height: number, alpha: number): void {
  scene.add.rectangle(x, y, width, height, BORDER_ENERGY_COLOR, alpha).setDepth(BORDER_WARNING_DEPTH);
}

function createHeroCenterLimitCues(scene: Phaser.Scene): void {
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const limitLeft = FLOOR_TILE_SIZE + HERO_RADIUS;
  const limitRight = WORLD_SIZE.x - FLOOR_TILE_SIZE - HERO_RADIUS;
  const limitTop = FLOOR_TILE_SIZE + HERO_RADIUS;
  const limitBottom = WORLD_SIZE.y - FLOOR_TILE_SIZE - HERO_RADIUS;
  const limitWidth = limitRight - limitLeft;
  const limitHeight = limitBottom - limitTop;
  const bandAlpha = 0.1;

  scene.add.rectangle(centerX, FLOOR_TILE_SIZE + HERO_RADIUS / 2, WORLD_SIZE.x - FLOOR_TILE_SIZE * 2, HERO_RADIUS, BORDER_DANGER_COLOR, bandAlpha).setDepth(BORDER_WARNING_DEPTH);
  scene.add
    .rectangle(centerX, WORLD_SIZE.y - FLOOR_TILE_SIZE - HERO_RADIUS / 2, WORLD_SIZE.x - FLOOR_TILE_SIZE * 2, HERO_RADIUS, BORDER_DANGER_COLOR, bandAlpha + 0.04)
    .setDepth(BORDER_WARNING_DEPTH);
  scene.add.rectangle(FLOOR_TILE_SIZE + HERO_RADIUS / 2, centerY, HERO_RADIUS, WORLD_SIZE.y - FLOOR_TILE_SIZE * 2, BORDER_DANGER_COLOR, bandAlpha).setDepth(BORDER_WARNING_DEPTH);
  scene.add
    .rectangle(WORLD_SIZE.x - FLOOR_TILE_SIZE - HERO_RADIUS / 2, centerY, HERO_RADIUS, WORLD_SIZE.y - FLOOR_TILE_SIZE * 2, BORDER_DANGER_COLOR, bandAlpha + 0.04)
    .setDepth(BORDER_WARNING_DEPTH);

  scene.add
    .rectangle(centerX, centerY, limitWidth, limitHeight, 0x000000, 0)
    .setStrokeStyle(2, 0x69dff6, 0.36)
    .setDepth(BORDER_WARNING_DEPTH + 1);
  scene.add.rectangle(centerX, limitTop, limitWidth - 112, 2, BORDER_DANGER_COLOR, 0.42).setDepth(BORDER_WARNING_DEPTH + 1);
  scene.add.rectangle(centerX, limitBottom, limitWidth - 112, 2, BORDER_DANGER_COLOR, 0.54).setDepth(BORDER_WARNING_DEPTH + 1);
  scene.add.rectangle(limitLeft, centerY, 2, limitHeight - 112, BORDER_DANGER_COLOR, 0.42).setDepth(BORDER_WARNING_DEPTH + 1);
  scene.add.rectangle(limitRight, centerY, 2, limitHeight - 112, BORDER_DANGER_COLOR, 0.54).setDepth(BORDER_WARNING_DEPTH + 1);
}

function createBoundaryWarningTicks(scene: Phaser.Scene): void {
  const start = FLOOR_TILE_SIZE * 2;
  const endX = WORLD_SIZE.x - FLOOR_TILE_SIZE * 2;
  const endY = WORLD_SIZE.y - FLOOR_TILE_SIZE * 2;
  const bottomY = WORLD_SIZE.y - FLOOR_TILE_SIZE - 7;
  const rightX = WORLD_SIZE.x - FLOOR_TILE_SIZE - 7;

  for (let x = start; x <= endX; x += FLOOR_TILE_SIZE * 2) {
    scene.add.rectangle(x, bottomY, 8, 26, BORDER_DANGER_COLOR, 0.56).setDepth(BORDER_WARNING_DEPTH);
  }

  for (let y = start; y <= endY; y += FLOOR_TILE_SIZE * 2) {
    scene.add.rectangle(rightX, y, 26, 8, BORDER_DANGER_COLOR, 0.56).setDepth(BORDER_WARNING_DEPTH);
  }
}
