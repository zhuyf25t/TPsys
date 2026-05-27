import Phaser from "phaser";
import {
  FLOOR_TEXTURE_KEY,
  FLOOR_TILE_SIZE,
  GLOBAL_BACKGROUND_PADDING,
  getActiveBattleMap,
  HERO_RADIUS,
  isNaturalBattleMapTheme,
  OUTSIDE_TEXTURE_KEY,
  STONE_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WORLD_SIZE,
  type BattleMapThemeId
} from "../../constants";

const OUT_OF_BOUNDS_SHADOW_DEPTH = -37;
const BORDER_SHADOW_DEPTH = 39;
const BORDER_WARNING_DEPTH = 40;
const BORDER_DANGER_COLOR = 0xd99a34;
const BORDER_ENERGY_COLOR = 0x58d6ff;
const BORDER_SHADOW_COLOR = 0x05070a;

interface NaturalMapPresentationPalette {
  outerBackground: number;
  playableBackground: number;
  cropStroke: number;
  leftBuffer: number;
  rightBuffer: number;
  groundSpecks: readonly number[];
  edgeAccent: number;
}

/** 中文名：创建竞技场presentationlayers（createArenaPresentationLayers）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createArenaPresentationLayers(scene: Phaser.Scene): void {
  if (isNaturalBattleMapTheme(getActiveBattleMap().themeId)) {
    createNaturalPresentationLayers(scene);
    return;
  }

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

function createNaturalPresentationLayers(scene: Phaser.Scene): void {
  const extendedWidth = WORLD_SIZE.x + GLOBAL_BACKGROUND_PADDING * 2;
  const extendedHeight = WORLD_SIZE.y + GLOBAL_BACKGROUND_PADDING * 2;
  const centerX = WORLD_SIZE.x / 2;
  const centerY = WORLD_SIZE.y / 2;
  const palette = naturalMapPaletteForTheme(getActiveBattleMap().themeId);

  scene.add.rectangle(centerX, centerY, extendedWidth, extendedHeight, palette.outerBackground, 1).setDepth(-40);
  scene.add.rectangle(centerX, centerY, WORLD_SIZE.x, WORLD_SIZE.y, palette.playableBackground, 0.78).setDepth(-30);

  getActiveBattleMap().terrainPatches.forEach((patch) => {
    const color = colorFromHex(patch.color);
    const object =
      patch.shape === "ellipse"
        ? scene.add.ellipse(patch.position.x, patch.position.y, patch.size.x, patch.size.y, color, patch.alpha)
        : scene.add.rectangle(patch.position.x, patch.position.y, patch.size.x, patch.size.y, color, patch.alpha);
    object.setDepth(terrainDepth(patch.kind));

    if (patch.rotation !== undefined) {
      object.setRotation(patch.rotation);
    }
  });

  createNaturalGroundTexture(scene, palette);
  createNaturalCropFrame(scene, palette);
  createBoundaryReadabilityLayer(scene);
}

function createNaturalGroundTexture(scene: Phaser.Scene, palette: NaturalMapPresentationPalette): void {
  for (let index = 0; index < 120; index += 1) {
    const x = 96 + ((index * 173) % (WORLD_SIZE.x - 192));
    const y = 84 + ((index * 251) % (WORLD_SIZE.y - 168));
    const color = palette.groundSpecks[index % palette.groundSpecks.length];
    scene.add
      .ellipse(x, y, 16 + (index % 4) * 5, 7 + (index % 3) * 3, color, 0.1 + (index % 5) * 0.012)
      .setRotation((index % 11) * 0.21)
      .setDepth(-6);
  }

  for (let x = FLOOR_TILE_SIZE; x < WORLD_SIZE.x; x += FLOOR_TILE_SIZE * 2) {
    scene.add.rectangle(x, WORLD_SIZE.y - FLOOR_TILE_SIZE - 8, 22, 4, palette.edgeAccent, 0.16).setDepth(39);
  }

  for (let y = FLOOR_TILE_SIZE; y < WORLD_SIZE.y; y += FLOOR_TILE_SIZE * 2) {
    scene.add.rectangle(WORLD_SIZE.x - FLOOR_TILE_SIZE - 8, y, 4, 22, palette.edgeAccent, 0.16).setDepth(39);
  }
}

function createNaturalCropFrame(scene: Phaser.Scene, palette: NaturalMapPresentationPalette): void {
  const crop = getActiveBattleMap().sourceCrop;
  const left = crop.offset.x;
  const top = crop.offset.y;
  const width = crop.crop.width * crop.scale;
  const height = crop.crop.height * crop.scale;

  scene.add.rectangle(left + width / 2, top + height / 2, width, height, 0x000000, 0).setStrokeStyle(3, palette.cropStroke, 0.28).setDepth(38);
  scene.add.rectangle(left / 2, WORLD_SIZE.y / 2, left, WORLD_SIZE.y, palette.leftBuffer, 0.12).setDepth(-4);
  scene.add.rectangle(WORLD_SIZE.x - left / 2, WORLD_SIZE.y / 2, left, WORLD_SIZE.y, palette.rightBuffer, 0.14).setDepth(-4);
}

function terrainDepth(kind: string): number {
  switch (kind) {
    case "water":
      return -18;
    case "trail":
      return -15;
    case "clearing":
    case "mud":
      return -16;
    case "grass":
      return -22;
    default:
      return -20;
  }
}

function naturalMapPaletteForTheme(themeId: BattleMapThemeId): NaturalMapPresentationPalette {
  switch (themeId) {
    case "winter":
      return {
        outerBackground: 0xb8d3dd,
        playableBackground: 0xd8eaf0,
        cropStroke: 0x83adbd,
        leftBuffer: 0xa8c4cf,
        rightBuffer: 0x9dbbc8,
        groundSpecks: [0xffffff, 0xeaf6fb, 0xcfe3ea, 0xa9c7d2],
        edgeAccent: 0x89b6c8
      };
    case "normal":
      return {
        outerBackground: 0x273f29,
        playableBackground: 0x4f7446,
        cropStroke: 0x8aa356,
        leftBuffer: 0x233b25,
        rightBuffer: 0x203721,
        groundSpecks: [0x6f8f4f, 0x8aa356, 0x4d6b3e, 0xa0b46a],
        edgeAccent: 0x8aa356
      };
    case "fall":
      return {
        outerBackground: 0x263528,
        playableBackground: 0x38533c,
        cropStroke: 0xb98b42,
        leftBuffer: 0x1d2b22,
        rightBuffer: 0x1b2a22,
        groundSpecks: [0xb06d24, 0xc18b2f, 0x8f4d2d, 0xd0a044, 0x5b6f3f],
        edgeAccent: 0xc69444
      };
    case "industrial":
      return {
        outerBackground: 0x263528,
        playableBackground: 0x38533c,
        cropStroke: 0xb98b42,
        leftBuffer: 0x1d2b22,
        rightBuffer: 0x1b2a22,
        groundSpecks: [0xb06d24],
        edgeAccent: 0xc69444
      };
  }
}

function colorFromHex(value: string): number {
  return Number.parseInt(value.replace("#", ""), 16);
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
