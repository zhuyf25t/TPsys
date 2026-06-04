import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  SourceCropDefinition,
  TerrainPatchDefinition
} from "../../../../../../objects/battle/microservices/world/objects/world/BattleWorldRuleDefinitions";
import type { BattleMapThemeId } from "../../../objects/BattleGameConstants";
import type {
  ArenaBackgroundEllipsePlan,
  ArenaBackgroundPatternPlan,
  ArenaBackgroundRectanglePlan,
  ArenaBackgroundStrokePlan,
  ArenaNaturalGroundTexturePlan,
  ArenaNaturalTerrainPatchPlan,
  NaturalMapPresentationPalette
} from "../objects/ArenaBackgroundObjects";

const BOUNDARY_SHADOW_DEPTH = 39;
const BOUNDARY_WARNING_DEPTH = 40;
const BOUNDARY_DANGER_COLOR = 0xd99a34;
const BOUNDARY_ENERGY_COLOR = 0x58d6ff;
const BOUNDARY_SHADOW_COLOR = 0x05070a;
const OUT_OF_BOUNDS_SHADOW_DEPTH = -37;
const OUT_OF_BOUNDS_SHADOW_COLOR = 0x020304;
const OUT_OF_BOUNDS_RAIL_COLOR = 0x0b1216;
const OUT_OF_BOUNDS_GLINT_COLOR = 0xc58f39;
const METAL_FLOOR_ENERGY_COLOR = 0x58d6ff;
const METAL_PANEL_SEAM_COLOR = 0x05090c;
const METAL_PANEL_RIVET_COLOR = 0x60727a;
const METAL_PANEL_GOLD_COLOR = 0xd99a34;
const METAL_PANEL_ENERGY_COLOR = 0x58d6ff;
const METAL_CORNER_SHADOW_COLOR = 0x020405;
const METAL_CORNER_ACCENT_COLOR = 0x23333a;

type OutOfBoundsEdge = "top" | "bottom" | "left" | "right";

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

export function naturalMapPaletteForTheme(themeId: BattleMapThemeId): NaturalMapPresentationPalette {
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

export function resolveNaturalBackgroundRectangles(
  worldSize: Vec2,
  padding: number,
  palette: NaturalMapPresentationPalette
): ArenaBackgroundRectanglePlan[] {
  const extendedWidth = worldSize.x + padding * 2;
  const extendedHeight = worldSize.y + padding * 2;
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    rectanglePlan(centerX, centerY, extendedWidth, extendedHeight, palette.outerBackground, 1, -40),
    rectanglePlan(centerX, centerY, worldSize.x, worldSize.y, palette.playableBackground, 0.78, -30)
  ];
}

export function resolveNaturalTerrainPatchPlan(
  terrainPatches: readonly TerrainPatchDefinition[]
): ArenaNaturalTerrainPatchPlan {
  const rectangles: ArenaBackgroundRectanglePlan[] = [];
  const ellipses: ArenaBackgroundEllipsePlan[] = [];

  terrainPatches.forEach((patch) => {
    const color = colorFromHex(patch.color);
    const depth = terrainDepth(patch.kind);

    if (patch.shape === "ellipse") {
      ellipses.push({
        position: patch.position,
        size: patch.size,
        color,
        alpha: patch.alpha,
        depth,
        rotation: patch.rotation
      });
      return;
    }

    rectangles.push(rectanglePlan(patch.position.x, patch.position.y, patch.size.x, patch.size.y, color, patch.alpha, depth, undefined, patch.rotation));
  });

  return { rectangles, ellipses };
}

export function resolveIndustrialArenaShellPatternPlans(worldSize: Vec2, padding: number): ArenaBackgroundPatternPlan[] {
  const extendedWidth = worldSize.x + padding * 2;
  const extendedHeight = worldSize.y + padding * 2;
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    patternPlan(centerX, centerY, extendedWidth, extendedHeight, "outside", -40, 0.98, 0x11171b),
    patternPlan(centerX, centerY, extendedWidth - 180, extendedHeight - 180, "stone", -39, 0.18, 0x1b2428),
    patternPlan(128, 160, 680, 520, "stoneTrim", -38, 0.12, 0x0a1014),
    patternPlan(worldSize.x - 128, 160, 680, 520, "stoneTrim", -38, 0.12, 0x0a1014),
    patternPlan(128, worldSize.y - 160, 680, 520, "stoneTrim", -38, 0.12, 0x0a1014),
    patternPlan(worldSize.x - 128, worldSize.y - 160, 680, 520, "stoneTrim", -38, 0.12, 0x0a1014)
  ];
}

export function resolveMetalArenaFloorPatternPlans(worldSize: Vec2): ArenaBackgroundPatternPlan[] {
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    patternPlan(centerX, centerY, worldSize.x, worldSize.y, "floor", -20, 1, 0x1a2529),
    patternPlan(centerX, centerY, worldSize.x - 192, worldSize.y - 192, "stone", -18, 0.3, 0x263239)
  ];
}

export function resolveNaturalGroundTexturePlan(
  worldSize: Vec2,
  floorTileSize: number,
  palette: NaturalMapPresentationPalette
): ArenaNaturalGroundTexturePlan {
  const specks: ArenaBackgroundEllipsePlan[] = [];
  const edgeAccents: ArenaBackgroundRectanglePlan[] = [];

  for (let index = 0; index < 120; index += 1) {
    specks.push({
      position: {
        x: 96 + ((index * 173) % (worldSize.x - 192)),
        y: 84 + ((index * 251) % (worldSize.y - 168))
      },
      size: {
        x: 16 + (index % 4) * 5,
        y: 7 + (index % 3) * 3
      },
      color: palette.groundSpecks[index % palette.groundSpecks.length],
      alpha: 0.1 + (index % 5) * 0.012,
      depth: -6,
      rotation: (index % 11) * 0.21
    });
  }

  for (let x = floorTileSize; x < worldSize.x; x += floorTileSize * 2) {
    edgeAccents.push(rectanglePlan(x, worldSize.y - floorTileSize - 8, 22, 4, palette.edgeAccent, 0.16, 39));
  }

  for (let y = floorTileSize; y < worldSize.y; y += floorTileSize * 2) {
    edgeAccents.push(rectanglePlan(worldSize.x - floorTileSize - 8, y, 4, 22, palette.edgeAccent, 0.16, 39));
  }

  return { specks, edgeAccents };
}

export function resolveNaturalCropFrameRectangles(
  worldSize: Vec2,
  sourceCrop: SourceCropDefinition,
  palette: NaturalMapPresentationPalette
): ArenaBackgroundRectanglePlan[] {
  const left = sourceCrop.offset.x;
  const top = sourceCrop.offset.y;
  const width = sourceCrop.crop.width * sourceCrop.scale;
  const height = sourceCrop.crop.height * sourceCrop.scale;

  return [
    rectanglePlan(left + width / 2, top + height / 2, width, height, 0x000000, 0, 38, {
      width: 3,
      color: palette.cropStroke,
      alpha: 0.28
    }),
    rectanglePlan(left / 2, worldSize.y / 2, left, worldSize.y, palette.leftBuffer, 0.12, -4),
    rectanglePlan(worldSize.x - left / 2, worldSize.y / 2, left, worldSize.y, palette.rightBuffer, 0.14, -4)
  ];
}

export function resolveMetalArenaAccentRectangles(worldSize: Vec2, floorTileSize: number): ArenaBackgroundRectanglePlan[] {
  return [
    ...resolveMetalPanelSeamRectangles(worldSize, floorTileSize),
    ...resolveCentralPanelHierarchyRectangles(worldSize),
    ...resolveArenaLightStripRectangles(worldSize),
    ...resolveIndustrialCornerShadowRectangles(worldSize)
  ];
}

export function resolveMetalArenaFloorRectangles(worldSize: Vec2): ArenaBackgroundRectanglePlan[] {
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    rectanglePlan(centerX, centerY, worldSize.x, worldSize.y, 0x020507, 0.12, -19),
    rectanglePlan(centerX, centerY, worldSize.x - 240, worldSize.y - 240, 0x091015, 0.36, -17),
    rectanglePlan(centerX, centerY, 1544, 936, 0x1b252b, 0.74, -16, {
      width: 8,
      color: 0x070a0d,
      alpha: 0.78
    }),
    rectanglePlan(centerX, centerY, 1392, 784, 0x223038, 0.54, -15, {
      width: 5,
      color: 0xc08a31,
      alpha: 0.36
    }),
    rectanglePlan(centerX, centerY, 1192, 584, 0x0f171c, 0.28, -14, {
      width: 3,
      color: METAL_FLOOR_ENERGY_COLOR,
      alpha: 0.28
    }),
    rectanglePlan(centerX, centerY, 760, 368, 0x27353b, 0.28, -13, {
      width: 2,
      color: 0xf0bf54,
      alpha: 0.2
    })
  ];
}

export function resolveBoundaryReadabilityRectangles(
  worldSize: Vec2,
  floorTileSize: number,
  heroRadius: number
): ArenaBackgroundRectanglePlan[] {
  const halfTile = floorTileSize / 2;
  const playableWidth = worldSize.x - floorTileSize * 2;
  const playableHeight = worldSize.y - floorTileSize * 2;
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;
  const innerLeft = floorTileSize;
  const innerRight = worldSize.x - floorTileSize;
  const innerTop = floorTileSize;
  const innerBottom = worldSize.y - floorTileSize;

  return [
    rectanglePlan(centerX, halfTile, worldSize.x, floorTileSize, BOUNDARY_SHADOW_COLOR, 0.18, BOUNDARY_SHADOW_DEPTH),
    rectanglePlan(centerX, worldSize.y - halfTile, worldSize.x, floorTileSize, BOUNDARY_SHADOW_COLOR, 0.28, BOUNDARY_SHADOW_DEPTH),
    rectanglePlan(halfTile, centerY, floorTileSize, worldSize.y, BOUNDARY_SHADOW_COLOR, 0.18, BOUNDARY_SHADOW_DEPTH),
    rectanglePlan(worldSize.x - halfTile, centerY, floorTileSize, worldSize.y, BOUNDARY_SHADOW_COLOR, 0.28, BOUNDARY_SHADOW_DEPTH),
    rectanglePlan(centerX, centerY, playableWidth, playableHeight, 0x000000, 0, BOUNDARY_SHADOW_DEPTH, {
      width: 6,
      color: 0x030608,
      alpha: 0.62
    }),
    rectanglePlan(centerX, innerTop + 8, playableWidth, 5, BOUNDARY_DANGER_COLOR, 0.22, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(centerX, innerBottom - 8, playableWidth, 6, BOUNDARY_DANGER_COLOR, 0.38, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(innerLeft + 8, centerY, 5, playableHeight, BOUNDARY_DANGER_COLOR, 0.22, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(innerRight - 8, centerY, 6, playableHeight, BOUNDARY_DANGER_COLOR, 0.38, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(centerX, innerTop + 18, playableWidth - 96, 2, BOUNDARY_ENERGY_COLOR, 0.14, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(centerX, innerBottom - 18, playableWidth - 96, 2, BOUNDARY_ENERGY_COLOR, 0.22, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(innerLeft + 18, centerY, 2, playableHeight - 96, BOUNDARY_ENERGY_COLOR, 0.14, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(innerRight - 18, centerY, 2, playableHeight - 96, BOUNDARY_ENERGY_COLOR, 0.22, BOUNDARY_WARNING_DEPTH),
    ...resolveHeroCenterLimitCueRectangles(worldSize, floorTileSize, heroRadius),
    ...resolveBoundaryWarningTickRectangles(worldSize, floorTileSize)
  ];
}

export function resolveOutOfBoundsShadowRectangles(
  worldSize: Vec2,
  floorTileSize: number,
  padding: number
): ArenaBackgroundRectanglePlan[] {
  const extendedWidth = worldSize.x + padding * 2;
  const extendedHeight = worldSize.y + padding * 2;
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;
  const farHazeAlpha = 0.08;
  const strongFarHazeAlpha = 0.12;

  return [
    rectanglePlan(centerX, -padding / 2, extendedWidth, padding, OUT_OF_BOUNDS_SHADOW_COLOR, farHazeAlpha, OUT_OF_BOUNDS_SHADOW_DEPTH),
    rectanglePlan(centerX, worldSize.y + padding / 2, extendedWidth, padding, OUT_OF_BOUNDS_SHADOW_COLOR, strongFarHazeAlpha, OUT_OF_BOUNDS_SHADOW_DEPTH),
    rectanglePlan(-padding / 2, centerY, padding, extendedHeight, OUT_OF_BOUNDS_SHADOW_COLOR, farHazeAlpha, OUT_OF_BOUNDS_SHADOW_DEPTH),
    rectanglePlan(worldSize.x + padding / 2, centerY, padding, extendedHeight, OUT_OF_BOUNDS_SHADOW_COLOR, strongFarHazeAlpha, OUT_OF_BOUNDS_SHADOW_DEPTH),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "top", 24, 48, 0.18),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "top", 84, 44, 0.11),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "bottom", 24, 56, 0.28),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "bottom", 92, 52, 0.15),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "left", 24, 48, 0.18),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "left", 84, 44, 0.11),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "right", 24, 56, 0.28),
    ...resolveOutOfBoundsEdgeBandRectangles(worldSize, floorTileSize, "right", 92, 52, 0.15),
    ...resolveOutOfBoundsRailCueRectangles(worldSize, floorTileSize)
  ];
}

function resolveHeroCenterLimitCueRectangles(
  worldSize: Vec2,
  floorTileSize: number,
  heroRadius: number
): ArenaBackgroundRectanglePlan[] {
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;
  const limitLeft = floorTileSize + heroRadius;
  const limitRight = worldSize.x - floorTileSize - heroRadius;
  const limitTop = floorTileSize + heroRadius;
  const limitBottom = worldSize.y - floorTileSize - heroRadius;
  const limitWidth = limitRight - limitLeft;
  const limitHeight = limitBottom - limitTop;
  const bandAlpha = 0.1;

  return [
    rectanglePlan(centerX, floorTileSize + heroRadius / 2, worldSize.x - floorTileSize * 2, heroRadius, BOUNDARY_DANGER_COLOR, bandAlpha, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(
      centerX,
      worldSize.y - floorTileSize - heroRadius / 2,
      worldSize.x - floorTileSize * 2,
      heroRadius,
      BOUNDARY_DANGER_COLOR,
      bandAlpha + 0.04,
      BOUNDARY_WARNING_DEPTH
    ),
    rectanglePlan(floorTileSize + heroRadius / 2, centerY, heroRadius, worldSize.y - floorTileSize * 2, BOUNDARY_DANGER_COLOR, bandAlpha, BOUNDARY_WARNING_DEPTH),
    rectanglePlan(
      worldSize.x - floorTileSize - heroRadius / 2,
      centerY,
      heroRadius,
      worldSize.y - floorTileSize * 2,
      BOUNDARY_DANGER_COLOR,
      bandAlpha + 0.04,
      BOUNDARY_WARNING_DEPTH
    ),
    rectanglePlan(centerX, centerY, limitWidth, limitHeight, 0x000000, 0, BOUNDARY_WARNING_DEPTH + 1, {
      width: 2,
      color: 0x69dff6,
      alpha: 0.36
    }),
    rectanglePlan(centerX, limitTop, limitWidth - 112, 2, BOUNDARY_DANGER_COLOR, 0.42, BOUNDARY_WARNING_DEPTH + 1),
    rectanglePlan(centerX, limitBottom, limitWidth - 112, 2, BOUNDARY_DANGER_COLOR, 0.54, BOUNDARY_WARNING_DEPTH + 1),
    rectanglePlan(limitLeft, centerY, 2, limitHeight - 112, BOUNDARY_DANGER_COLOR, 0.42, BOUNDARY_WARNING_DEPTH + 1),
    rectanglePlan(limitRight, centerY, 2, limitHeight - 112, BOUNDARY_DANGER_COLOR, 0.54, BOUNDARY_WARNING_DEPTH + 1)
  ];
}

function resolveBoundaryWarningTickRectangles(worldSize: Vec2, floorTileSize: number): ArenaBackgroundRectanglePlan[] {
  const rectangles: ArenaBackgroundRectanglePlan[] = [];
  const start = floorTileSize * 2;
  const endX = worldSize.x - floorTileSize * 2;
  const endY = worldSize.y - floorTileSize * 2;
  const bottomY = worldSize.y - floorTileSize - 7;
  const rightX = worldSize.x - floorTileSize - 7;

  for (let x = start; x <= endX; x += floorTileSize * 2) {
    rectangles.push(rectanglePlan(x, bottomY, 8, 26, BOUNDARY_DANGER_COLOR, 0.56, BOUNDARY_WARNING_DEPTH));
  }

  for (let y = start; y <= endY; y += floorTileSize * 2) {
    rectangles.push(rectanglePlan(rightX, y, 26, 8, BOUNDARY_DANGER_COLOR, 0.56, BOUNDARY_WARNING_DEPTH));
  }

  return rectangles;
}

function resolveOutOfBoundsEdgeBandRectangles(
  worldSize: Vec2,
  floorTileSize: number,
  edge: OutOfBoundsEdge,
  offset: number,
  thickness: number,
  alpha: number
): ArenaBackgroundRectanglePlan[] {
  const horizontalWidth = worldSize.x + floorTileSize * 2;
  const verticalHeight = worldSize.y + floorTileSize * 2;
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  if (edge === "top") {
    return [rectanglePlan(centerX, -offset, horizontalWidth, thickness, OUT_OF_BOUNDS_SHADOW_COLOR, alpha, OUT_OF_BOUNDS_SHADOW_DEPTH)];
  }

  if (edge === "bottom") {
    return [rectanglePlan(centerX, worldSize.y + offset, horizontalWidth, thickness, OUT_OF_BOUNDS_SHADOW_COLOR, alpha, OUT_OF_BOUNDS_SHADOW_DEPTH)];
  }

  if (edge === "left") {
    return [rectanglePlan(-offset, centerY, thickness, verticalHeight, OUT_OF_BOUNDS_SHADOW_COLOR, alpha, OUT_OF_BOUNDS_SHADOW_DEPTH)];
  }

  return [rectanglePlan(worldSize.x + offset, centerY, thickness, verticalHeight, OUT_OF_BOUNDS_SHADOW_COLOR, alpha, OUT_OF_BOUNDS_SHADOW_DEPTH)];
}

function resolveOutOfBoundsRailCueRectangles(worldSize: Vec2, floorTileSize: number): ArenaBackgroundRectanglePlan[] {
  const rectangles: ArenaBackgroundRectanglePlan[] = [];
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  rectangles.push(
    rectanglePlan(centerX, -6, worldSize.x + floorTileSize, 8, OUT_OF_BOUNDS_RAIL_COLOR, 0.72, OUT_OF_BOUNDS_SHADOW_DEPTH + 1),
    rectanglePlan(centerX, worldSize.y + 6, worldSize.x + floorTileSize, 10, OUT_OF_BOUNDS_RAIL_COLOR, 0.86, OUT_OF_BOUNDS_SHADOW_DEPTH + 1),
    rectanglePlan(-6, centerY, 8, worldSize.y + floorTileSize, OUT_OF_BOUNDS_RAIL_COLOR, 0.72, OUT_OF_BOUNDS_SHADOW_DEPTH + 1),
    rectanglePlan(worldSize.x + 6, centerY, 10, worldSize.y + floorTileSize, OUT_OF_BOUNDS_RAIL_COLOR, 0.86, OUT_OF_BOUNDS_SHADOW_DEPTH + 1)
  );

  for (let x = floorTileSize; x < worldSize.x; x += floorTileSize * 3) {
    rectangles.push(
      rectanglePlan(x, -12, 34, 5, OUT_OF_BOUNDS_GLINT_COLOR, 0.2, OUT_OF_BOUNDS_SHADOW_DEPTH + 2),
      rectanglePlan(x, worldSize.y + 12, 42, 6, OUT_OF_BOUNDS_GLINT_COLOR, 0.36, OUT_OF_BOUNDS_SHADOW_DEPTH + 2)
    );
  }

  for (let y = floorTileSize; y < worldSize.y; y += floorTileSize * 3) {
    rectangles.push(
      rectanglePlan(-12, y, 5, 34, OUT_OF_BOUNDS_GLINT_COLOR, 0.2, OUT_OF_BOUNDS_SHADOW_DEPTH + 2),
      rectanglePlan(worldSize.x + 12, y, 6, 42, OUT_OF_BOUNDS_GLINT_COLOR, 0.36, OUT_OF_BOUNDS_SHADOW_DEPTH + 2)
    );
  }

  return rectangles;
}

function resolveMetalPanelSeamRectangles(worldSize: Vec2, floorTileSize: number): ArenaBackgroundRectanglePlan[] {
  const rectangles: ArenaBackgroundRectanglePlan[] = [];
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  for (let x = floorTileSize * 3; x < worldSize.x - floorTileSize * 3; x += floorTileSize * 3) {
    rectangles.push(rectanglePlan(x, centerY, 2, worldSize.y - floorTileSize * 4, METAL_PANEL_SEAM_COLOR, 0.22, -12));
  }

  for (let y = floorTileSize * 3; y < worldSize.y - floorTileSize * 3; y += floorTileSize * 3) {
    rectangles.push(rectanglePlan(centerX, y, worldSize.x - floorTileSize * 4, 2, METAL_PANEL_SEAM_COLOR, 0.22, -12));
  }

  for (let x = floorTileSize * 4; x < worldSize.x - floorTileSize * 4; x += floorTileSize * 6) {
    rectangles.push(
      rectanglePlan(x, centerY - 304, 8, 8, METAL_PANEL_RIVET_COLOR, 0.24, -11),
      rectanglePlan(x, centerY + 304, 8, 8, METAL_PANEL_RIVET_COLOR, 0.24, -11)
    );
  }

  for (let y = floorTileSize * 4; y < worldSize.y - floorTileSize * 4; y += floorTileSize * 5) {
    rectangles.push(
      rectanglePlan(centerX - 544, y, 8, 8, METAL_PANEL_RIVET_COLOR, 0.18, -11),
      rectanglePlan(centerX + 544, y, 8, 8, METAL_PANEL_RIVET_COLOR, 0.18, -11)
    );
  }

  return rectangles;
}

function resolveCentralPanelHierarchyRectangles(worldSize: Vec2): ArenaBackgroundRectanglePlan[] {
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    rectanglePlan(centerX, centerY, 1072, 328, 0x18242a, 0.16, -12, {
      width: 2,
      color: 0x41535a,
      alpha: 0.16
    }),
    rectanglePlan(centerX, centerY, 820, 220, 0x0a1217, 0.14, -11, {
      width: 2,
      color: METAL_PANEL_GOLD_COLOR,
      alpha: 0.18
    }),
    rectanglePlan(centerX, centerY - 164, 840, 3, METAL_PANEL_ENERGY_COLOR, 0.18, -9),
    rectanglePlan(centerX, centerY + 164, 840, 3, METAL_PANEL_ENERGY_COLOR, 0.18, -9),
    rectanglePlan(centerX - 536, centerY, 3, 300, METAL_PANEL_GOLD_COLOR, 0.16, -9),
    rectanglePlan(centerX + 536, centerY, 3, 300, METAL_PANEL_GOLD_COLOR, 0.16, -9),
    rectanglePlan(centerX - 224, centerY, 124, 4, METAL_PANEL_GOLD_COLOR, 0.22, -8),
    rectanglePlan(centerX + 224, centerY, 124, 4, METAL_PANEL_GOLD_COLOR, 0.22, -8),
    rectanglePlan(centerX, centerY - 88, 4, 82, METAL_PANEL_ENERGY_COLOR, 0.18, -8),
    rectanglePlan(centerX, centerY + 88, 4, 82, METAL_PANEL_ENERGY_COLOR, 0.18, -8)
  ];
}

function resolveArenaLightStripRectangles(worldSize: Vec2): ArenaBackgroundRectanglePlan[] {
  const centerX = worldSize.x / 2;
  const centerY = worldSize.y / 2;

  return [
    rectanglePlan(centerX, centerY - 420, 1024, 5, METAL_PANEL_GOLD_COLOR, 0.28, -10),
    rectanglePlan(centerX, centerY + 420, 1024, 5, METAL_PANEL_GOLD_COLOR, 0.34, -10),
    rectanglePlan(centerX - 680, centerY, 5, 560, METAL_PANEL_ENERGY_COLOR, 0.16, -10),
    rectanglePlan(centerX + 680, centerY, 5, 560, METAL_PANEL_ENERGY_COLOR, 0.22, -10),
    rectanglePlan(centerX, centerY - 292, 560, 3, METAL_PANEL_ENERGY_COLOR, 0.22, -9),
    rectanglePlan(centerX, centerY + 292, 560, 3, METAL_PANEL_ENERGY_COLOR, 0.2, -9),
    rectanglePlan(centerX - 424, centerY, 3, 280, METAL_PANEL_GOLD_COLOR, 0.2, -9),
    rectanglePlan(centerX + 424, centerY, 3, 280, METAL_PANEL_GOLD_COLOR, 0.2, -9)
  ];
}

function resolveIndustrialCornerShadowRectangles(worldSize: Vec2): ArenaBackgroundRectanglePlan[] {
  return [
    rectanglePlan(224, 224, 448, 448, METAL_CORNER_SHADOW_COLOR, 0.3, -13),
    rectanglePlan(worldSize.x - 224, 224, 448, 448, METAL_CORNER_SHADOW_COLOR, 0.3, -13),
    rectanglePlan(224, worldSize.y - 224, 448, 448, METAL_CORNER_SHADOW_COLOR, 0.38, -13),
    rectanglePlan(worldSize.x - 224, worldSize.y - 224, 448, 448, METAL_CORNER_SHADOW_COLOR, 0.38, -13),
    rectanglePlan(368, 368, 232, 18, METAL_CORNER_ACCENT_COLOR, 0.22, -12),
    rectanglePlan(worldSize.x - 368, 368, 232, 18, METAL_CORNER_ACCENT_COLOR, 0.22, -12),
    rectanglePlan(368, worldSize.y - 368, 232, 18, METAL_CORNER_ACCENT_COLOR, 0.26, -12),
    rectanglePlan(worldSize.x - 368, worldSize.y - 368, 232, 18, METAL_CORNER_ACCENT_COLOR, 0.26, -12)
  ];
}

function rectanglePlan(
  x: number,
  y: number,
  width: number,
  height: number,
  color: number,
  alpha: number,
  depth: number,
  stroke?: ArenaBackgroundStrokePlan,
  rotation?: number
): ArenaBackgroundRectanglePlan {
  return {
    position: { x, y },
    size: { x: width, y: height },
    color,
    alpha,
    depth,
    rotation,
    stroke
  };
}

function patternPlan(
  x: number,
  y: number,
  width: number,
  height: number,
  textureRole: ArenaBackgroundPatternPlan["textureRole"],
  depth: number,
  alpha: number,
  tint?: number
): ArenaBackgroundPatternPlan {
  return {
    position: { x, y },
    size: { x: width, y: height },
    textureRole,
    depth,
    alpha,
    tint
  };
}
