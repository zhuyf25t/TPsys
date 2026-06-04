import Phaser from "phaser";
import {
  naturalMapPaletteForTheme,
  resolveIndustrialArenaShellPatternPlans,
  resolveBoundaryReadabilityRectangles,
  resolveMetalArenaAccentRectangles,
  resolveMetalArenaFloorRectangles,
  resolveMetalArenaFloorPatternPlans,
  resolveNaturalBackgroundRectangles,
  resolveNaturalCropFrameRectangles,
  resolveNaturalGroundTexturePlan,
  resolveNaturalTerrainPatchPlan,
  resolveOutOfBoundsShadowRectangles
} from "./functions/ArenaBackgroundRules";
import type {
  ArenaBackgroundEllipsePlan,
  ArenaBackgroundPatternPlan,
  ArenaBackgroundTextureRole,
  ArenaBackgroundRectanglePlan,
  NaturalMapPresentationPalette
} from "./objects/ArenaBackgroundObjects";
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
  WORLD_SIZE
} from "../../objects/BattleGameConstants";


/** 涓枃鍚嶏細鍒涘缓绔炴妧鍦簆resentationlayers锛坈reateArenaPresentationLayers锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷淬€?*/
export function createArenaPresentationLayers(scene: Phaser.Scene): void {
  if (isNaturalBattleMapTheme(getActiveBattleMap().themeId)) {
    createNaturalPresentationLayers(scene);
    return;
  }

  createIndustrialArenaShellPatterns(scene);
  createOutOfBoundsShadow(scene);

  createMetalArenaFloor(scene);
  createBoundaryReadabilityLayer(scene);
}

function createIndustrialArenaShellPatterns(scene: Phaser.Scene): void {
  resolveIndustrialArenaShellPatternPlans(WORLD_SIZE, GLOBAL_BACKGROUND_PADDING).forEach((pattern) => {
    createBackgroundPattern(scene, pattern);
  });
}

function createNaturalPresentationLayers(scene: Phaser.Scene): void {
  const activeMap = getActiveBattleMap();
  const palette = naturalMapPaletteForTheme(activeMap.themeId);
  const terrainPlan = resolveNaturalTerrainPatchPlan(activeMap.terrainPatches);

  resolveNaturalBackgroundRectangles(WORLD_SIZE, GLOBAL_BACKGROUND_PADDING, palette).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });

  terrainPlan.rectangles.forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });

  terrainPlan.ellipses.forEach((ellipse) => {
    createBackgroundEllipse(scene, ellipse);
  });

  createNaturalGroundTexture(scene, palette);
  createNaturalCropFrame(scene, palette);
  createBoundaryReadabilityLayer(scene);
}

function createNaturalGroundTexture(scene: Phaser.Scene, palette: NaturalMapPresentationPalette): void {
  const plan = resolveNaturalGroundTexturePlan(WORLD_SIZE, FLOOR_TILE_SIZE, palette);

  plan.specks.forEach((ellipse) => {
    createBackgroundEllipse(scene, ellipse);
  });

  plan.edgeAccents.forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

function createNaturalCropFrame(scene: Phaser.Scene, palette: NaturalMapPresentationPalette): void {
  resolveNaturalCropFrameRectangles(WORLD_SIZE, getActiveBattleMap().sourceCrop, palette).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

function createMetalArenaFloor(scene: Phaser.Scene): void {
  resolveMetalArenaFloorPatternPlans(WORLD_SIZE).forEach((pattern) => {
    createBackgroundPattern(scene, pattern);
  });

  createMetalArenaFloorBaseLayer(scene);
  createMetalArenaAccentLayer(scene);
}

function createMetalArenaFloorBaseLayer(scene: Phaser.Scene): void {
  resolveMetalArenaFloorRectangles(WORLD_SIZE).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

function createMetalArenaAccentLayer(scene: Phaser.Scene): void {
  resolveMetalArenaAccentRectangles(WORLD_SIZE, FLOOR_TILE_SIZE).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

function createOutOfBoundsShadow(scene: Phaser.Scene): void {
  resolveOutOfBoundsShadowRectangles(WORLD_SIZE, FLOOR_TILE_SIZE, GLOBAL_BACKGROUND_PADDING).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

// Visual-only cues for the collision border; obstacleBounds remain unchanged.
function createBoundaryReadabilityLayer(scene: Phaser.Scene): void {
  resolveBoundaryReadabilityRectangles(WORLD_SIZE, FLOOR_TILE_SIZE, HERO_RADIUS).forEach((rectangle) => {
    createBackgroundRectangle(scene, rectangle);
  });
}

function createBackgroundRectangle(scene: Phaser.Scene, plan: ArenaBackgroundRectanglePlan): void {
  const rectangle = scene.add
    .rectangle(plan.position.x, plan.position.y, plan.size.x, plan.size.y, plan.color, plan.alpha)
    .setDepth(plan.depth);

  if (plan.stroke !== undefined) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  if (plan.rotation !== undefined) {
    rectangle.setRotation(plan.rotation);
  }
}

function createBackgroundEllipse(scene: Phaser.Scene, plan: ArenaBackgroundEllipsePlan): void {
  const ellipse = scene.add
    .ellipse(plan.position.x, plan.position.y, plan.size.x, plan.size.y, plan.color, plan.alpha)
    .setDepth(plan.depth);

  if (plan.rotation !== undefined) {
    ellipse.setRotation(plan.rotation);
  }
}

function createBackgroundPattern(scene: Phaser.Scene, plan: ArenaBackgroundPatternPlan): void {
  const sprite = scene.add
    .tileSprite(plan.position.x, plan.position.y, plan.size.x, plan.size.y, textureKeyForRole(plan.textureRole))
    .setDepth(plan.depth)
    .setAlpha(plan.alpha);

  if (plan.tint !== undefined) {
    sprite.setTint(plan.tint);
  }
}

function textureKeyForRole(textureRole: ArenaBackgroundTextureRole): string {
  switch (textureRole) {
    case "floor":
      return FLOOR_TEXTURE_KEY;
    case "outside":
      return OUTSIDE_TEXTURE_KEY;
    case "stone":
      return STONE_TEXTURE_KEY;
    case "stoneTrim":
      return STONE_TRIM_TEXTURE_KEY;
  }
}
