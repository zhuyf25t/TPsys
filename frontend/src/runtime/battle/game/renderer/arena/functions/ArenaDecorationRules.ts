import type { PickupSpawnPoint } from "../../../../../../objects/battle/microservices/world/objects/world/PickupSpawnPoint";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleMapThemeId } from "../../../objects/BattleGameConstants";
import type {
  ArenaDecorationElementPlan,
  ArenaDecorationEllipsePlan,
  ArenaDecorationImagePlan,
  ArenaDecorationPatternPlan,
  ArenaDecorationPresentationPlan,
  ArenaDecorationRectanglePlan,
  ArenaDecorationStrokePlan,
  ArenaPickupPadPresentationPlan,
  NaturalPickupPadPalette
} from "../objects/ArenaDecorationObjects";

const ARENA_ENERGY_ACCENT_COLOR = 0x58d6ff;

const INDUSTRIAL_PYLON_POSITIONS: readonly Vec2[] = [
  { x: 320, y: 320 },
  { x: 2240, y: 320 },
  { x: 320, y: 1280 },
  { x: 2240, y: 1280 },
  { x: 736, y: 224 },
  { x: 1824, y: 224 },
  { x: 736, y: 1376 },
  { x: 1824, y: 1376 }
];

const INDUSTRIAL_MACHINERY_POSITIONS: readonly Vec2[] = [
  { x: 896, y: 448 },
  { x: 1664, y: 448 },
  { x: 896, y: 1152 },
  { x: 1664, y: 1152 },
  { x: 640, y: 640 },
  { x: 1920, y: 640 },
  { x: 640, y: 960 },
  { x: 1920, y: 960 }
];

const INDUSTRIAL_LOW_DECK_PLATE_POSITIONS: readonly Vec2[] = [
  { x: 544, y: 576 },
  { x: 2016, y: 576 },
  { x: 544, y: 1024 },
  { x: 2016, y: 1024 },
  { x: 1280, y: 224 },
  { x: 1280, y: 1376 }
];

export function resolveIndustrialArenaDecorationPresentationPlan(): ArenaDecorationPresentationPlan {
  const elements: ArenaDecorationElementPlan[] = [];

  INDUSTRIAL_PYLON_POSITIONS.forEach((position) => {
    elements.push(
      rectangleElement(position.x + 8, position.y + 16, 82, 96, 0x020405, 0.36, 43),
      imageElement(position.x, position.y, "wall", 1.16, 54, 0x1b252c, 0.96, 0.96),
      rectangleElement(position.x, position.y + 30, 78, 7, ARENA_ENERGY_ACCENT_COLOR, 0.2, 55)
    );
  });

  INDUSTRIAL_MACHINERY_POSITIONS.forEach((position) => {
    elements.push(
      rectangleElement(position.x + 10, position.y + 12, 74, 58, 0x020405, 0.32, 42),
      imageElement(position.x, position.y, "rock", 1.08, 53, 0x2b363b, 0.92, 0.92),
      rectangleElement(position.x, position.y - 24, 44, 4, 0xd99a34, 0.22, 54)
    );
  });

  INDUSTRIAL_LOW_DECK_PLATE_POSITIONS.forEach((position) => {
    elements.push(
      imageElement(position.x, position.y, "crate", 1.1, 11, 0x1f2b31, 0.78),
      rectangleElement(position.x, position.y, 74, 6, 0x5fd9ff, 0.14, 12)
    );
  });

  return { elements };
}

export function resolveIndustrialPickupPadPresentationPlan(
  weaponPickupSpawnPoints: readonly PickupSpawnPoint[],
  itemPickupSpawnPoints: readonly PickupSpawnPoint[]
): ArenaPickupPadPresentationPlan {
  const patterns: ArenaDecorationPatternPlan[] = [];
  const rectangles: ArenaDecorationRectanglePlan[] = [];

  weaponPickupSpawnPoints.forEach((point) => {
    patterns.push(patternPlan(point.position.x, point.position.y + 8, 112, 82, "stoneTrim", -12, 0.98, 0x29343a));
    rectangles.push(
      rectanglePlan(point.position.x, point.position.y + 8, 118, 88, 0x11181c, 0.18, -11, {
        width: 2,
        color: 0xd99a34,
        alpha: 0.72
      }),
      rectanglePlan(point.position.x, point.position.y + 8, 86, 50, 0xf0bd58, 0.06, -10, {
        width: 1,
        color: 0xf0bd58,
        alpha: 0.46
      })
    );
  });

  itemPickupSpawnPoints.forEach((point) => {
    patterns.push(patternPlan(point.position.x, point.position.y + 8, 100, 76, "stoneTrim", -12, 0.96, 0x21343c));
    rectangles.push(
      rectanglePlan(point.position.x, point.position.y + 8, 106, 82, 0x0d1a1e, 0.18, -11, {
        width: 2,
        color: ARENA_ENERGY_ACCENT_COLOR,
        alpha: 0.72
      }),
      rectanglePlan(point.position.x, point.position.y + 8, 72, 44, 0x8ff3ff, 0.06, -10, {
        width: 1,
        color: 0x8ff3ff,
        alpha: 0.4
      })
    );
  });

  return { patterns, rectangles, ellipses: [] };
}

export function resolveNaturalPickupPadPresentationPlan(
  themeId: BattleMapThemeId,
  weaponPickupSpawnPoints: readonly PickupSpawnPoint[],
  itemPickupSpawnPoints: readonly PickupSpawnPoint[]
): ArenaPickupPadPresentationPlan {
  const palette = naturalPickupPadPalette(themeId);
  const ellipses: ArenaDecorationEllipsePlan[] = [];

  weaponPickupSpawnPoints.forEach((point) => {
    ellipses.push(
      ellipsePlan(point.position.x + 5, point.position.y + 9, 104, 68, palette.weaponShadow, 0.18, 19),
      ellipsePlan(point.position.x, point.position.y + 4, 98, 58, palette.weaponOuter, 0.2, 20),
      ellipsePlan(point.position.x, point.position.y + 4, 68, 34, palette.weaponInner, 0.12, 21)
    );
  });

  itemPickupSpawnPoints.forEach((point) => {
    ellipses.push(
      ellipsePlan(point.position.x + 5, point.position.y + 9, 92, 62, palette.itemShadow, 0.18, 19),
      ellipsePlan(point.position.x, point.position.y + 4, 84, 50, palette.itemOuter, 0.22, 20),
      ellipsePlan(point.position.x, point.position.y + 4, 58, 30, palette.itemInner, 0.12, 21)
    );
  });

  return { patterns: [], rectangles: [], ellipses };
}

function naturalPickupPadPalette(themeId: BattleMapThemeId): NaturalPickupPadPalette {
  switch (themeId) {
    case "winter":
      return {
        weaponShadow: 0x8aa7b4,
        weaponOuter: 0xdceef5,
        weaponInner: 0xffffff,
        itemShadow: 0x7ea5b4,
        itemOuter: 0xb9dbe8,
        itemInner: 0xedfbff
      };
    case "normal":
      return {
        weaponShadow: 0x243018,
        weaponOuter: 0x7d8b3c,
        weaponInner: 0xd4b85a,
        itemShadow: 0x1f301d,
        itemOuter: 0x4f7b42,
        itemInner: 0x9fdd7a
      };
    case "fall":
      return {
        weaponShadow: 0x2b2f1d,
        weaponOuter: 0xa57634,
        weaponInner: 0xe0b15e,
        itemShadow: 0x20301f,
        itemOuter: 0x527546,
        itemInner: 0x9be77d
      };
    case "industrial":
      return {
        weaponShadow: 0x2b2f1d,
        weaponOuter: 0xa57634,
        weaponInner: 0xe0b15e,
        itemShadow: 0x20301f,
        itemOuter: 0x527546,
        itemInner: 0x9be77d
      };
  }
}

function patternPlan(
  x: number,
  y: number,
  width: number,
  height: number,
  textureRole: ArenaDecorationPatternPlan["textureRole"],
  depth: number,
  alpha: number,
  tint?: number
): ArenaDecorationPatternPlan {
  return {
    position: { x, y },
    size: { x: width, y: height },
    textureRole,
    depth,
    alpha,
    tint
  };
}

function rectangleElement(
  x: number,
  y: number,
  width: number,
  height: number,
  color: number,
  alpha: number,
  depth: number,
  stroke?: ArenaDecorationStrokePlan
): ArenaDecorationElementPlan {
  return {
    kind: "rectangle",
    rectangle: rectanglePlan(x, y, width, height, color, alpha, depth, stroke)
  };
}

function imageElement(
  x: number,
  y: number,
  textureRole: ArenaDecorationImagePlan["textureRole"],
  scale: number,
  depth: number,
  tint: number | undefined,
  alpha: number,
  occludableBaseAlpha?: number
): ArenaDecorationElementPlan {
  return {
    kind: "image",
    image: imagePlan(x, y, textureRole, scale, depth, tint, alpha, occludableBaseAlpha)
  };
}

function imagePlan(
  x: number,
  y: number,
  textureRole: ArenaDecorationImagePlan["textureRole"],
  scale: number,
  depth: number,
  tint: number | undefined,
  alpha: number,
  occludableBaseAlpha?: number
): ArenaDecorationImagePlan {
  return {
    position: { x, y },
    textureRole,
    scale,
    depth,
    tint,
    alpha,
    occludableBaseAlpha
  };
}

function rectanglePlan(
  x: number,
  y: number,
  width: number,
  height: number,
  color: number,
  alpha: number,
  depth: number,
  stroke?: ArenaDecorationStrokePlan
): ArenaDecorationRectanglePlan {
  return {
    position: { x, y },
    size: { x: width, y: height },
    color,
    alpha,
    depth,
    stroke
  };
}

function ellipsePlan(
  x: number,
  y: number,
  width: number,
  height: number,
  color: number,
  alpha: number,
  depth: number
): ArenaDecorationEllipsePlan {
  return {
    position: { x, y },
    size: { x: width, y: height },
    color,
    alpha,
    depth
  };
}
