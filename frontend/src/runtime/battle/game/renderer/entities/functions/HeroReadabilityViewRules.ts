import type { BattleSlowFieldState as SlowField } from "../../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import { resolveHeroVisual } from "../../../functions/BattleSpawnFactory";
import {
  WEAPON_CUE_READABILITY_STYLES,
  type HeroHealthVisualPlan,
  type HeroReadabilityCreationPlan,
  type HeroReadabilityStatusRingVisualPlan,
  type HeroReadabilityVisualPlan,
  type ResolveHeroReadabilityCreationPlanInput,
  type ResolveHeroReadabilityVisualPlanInput,
  type WeaponCueReadabilityStyle
} from "../objects/HeroReadabilityViewObjects";

const HERO_READABILITY_MARKER_DEPTH = 32;
const HERO_READABILITY_SHADOW_DEPTH = 33;
const HERO_READABILITY_BODY_DEPTH = 34;
const HERO_READABILITY_SILHOUETTE_DEPTH = 35;
const HERO_READABILITY_HIT_RING_DEPTH = 36;
const HERO_READABILITY_STATUS_RING_DEPTH = 37;
const HERO_READABILITY_WEAPON_STOCK_DEPTH = 38;
const HERO_READABILITY_WEAPON_CUE_DEPTH = 39;
const HERO_READABILITY_WEAPON_MUZZLE_DEPTH = 40;
const HERO_READABILITY_MIN_RADIUS = 18;
const HERO_READABILITY_SHADOW_RADIUS_SCALE = 1.08;
const HERO_READABILITY_SILHOUETTE_RADIUS_OFFSET = 1;
const HERO_READABILITY_STATUS_RING_RADIUS_OFFSET = 6;
const HERO_READABILITY_WEAPON_CUE_ORIGIN_OFFSET_RADIUS_SCALE = 0.22;
const HERO_READABILITY_SHADOW_TINT = 0x020711;
const HERO_READABILITY_SHADOW_LOCAL_ALPHA = 0.3;
const HERO_READABILITY_SHADOW_REMOTE_ALPHA = 0.22;
const HERO_READABILITY_BODY_LOCAL_ALPHA = 0.15;
const HERO_READABILITY_BODY_REMOTE_ALPHA = 0.1;
const HERO_READABILITY_SILHOUETTE_TINT = 0x000000;
const HERO_READABILITY_SILHOUETTE_FILL_ALPHA = 0;
const HERO_READABILITY_SILHOUETTE_STROKE_WIDTH = 3;
const HERO_READABILITY_SILHOUETTE_STROKE_TINT = 0x06101b;
const HERO_READABILITY_SILHOUETTE_LOCAL_STROKE_ALPHA = 0.58;
const HERO_READABILITY_SILHOUETTE_REMOTE_STROKE_ALPHA = 0.42;
const HERO_READABILITY_HIT_RING_FILL_ALPHA = 0;
const HERO_READABILITY_HIT_RING_LOCAL_STROKE_WIDTH = 2;
const HERO_READABILITY_HIT_RING_REMOTE_STROKE_WIDTH = 1;
const HERO_READABILITY_HIT_RING_LOCAL_STROKE_ALPHA = 0.62;
const HERO_READABILITY_HIT_RING_REMOTE_STROKE_ALPHA = 0.36;
const HERO_READABILITY_WEAPON_STOCK_ORIGIN: Vec2 = { x: 0.74, y: 0.5 };
const HERO_READABILITY_WEAPON_CUE_ORIGIN: Vec2 = { x: 0.08, y: 0.5 };
const HERO_READABILITY_WEAPON_STROKE_WIDTH = 1;
const HERO_READABILITY_MARKER_RADIUS_OFFSET = 8;
const HERO_READABILITY_MARKER_FILL_TINT = 0x4ad9ff;
const HERO_READABILITY_MARKER_FILL_ALPHA = 0.035;
const HERO_READABILITY_MARKER_STROKE_WIDTH = 2;
const HERO_READABILITY_MARKER_STROKE_TINT = 0x76e4ff;
const HERO_READABILITY_MARKER_STROKE_ALPHA = 0.55;
const HERO_HEALTH_FILL_WIDTH = 48;
const HERO_HEALTH_WARNING_RATIO = 0.55;
const HERO_HEALTH_DANGER_RATIO = 0.3;
const HERO_HEALTH_WARNING_TINT = 0xffc857;
const HERO_HEALTH_DANGER_TINT = 0xff5a4f;
const HERO_HEALTH_BACKGROUND_TINT = 0x0d1014;
const HERO_HEALTH_BACKGROUND_NORMAL_ALPHA = 0.95;
const HERO_SLOWED_STATUS_TINT = 0x9bf8ff;
const HERO_SLOWED_STATUS_FILL_ALPHA = 0.055;
const HERO_SLOWED_STATUS_STROKE_WIDTH = 2;
const HERO_SLOWED_STATUS_STROKE_ALPHA = 0.58;

export function resolveHeroReadabilityRadius(radius: number): number {
  return Math.max(HERO_READABILITY_MIN_RADIUS, Number.isFinite(radius) ? radius : HERO_READABILITY_MIN_RADIUS);
}

export function resolveHeroWeaponKind(hero: Hero): WeaponKind {
  return hero.weapons[hero.currentWeaponIndex]?.weaponKind ?? "Pistol";
}

export function getWeaponCueReadabilityStyle(weaponKind: WeaponKind): WeaponCueReadabilityStyle {
  return WEAPON_CUE_READABILITY_STYLES[weaponKind];
}

export function isHeroInsideSlowField(hero: Hero, slowFields: readonly SlowField[]): boolean {
  if (!isFiniteVec2(hero.position)) {
    return false;
  }

  return slowFields.some((field) => {
    if (!isFiniteVec2(field.position) || !Number.isFinite(field.radius) || field.radius <= 0) {
      return false;
    }

    return distanceBetween(hero.position, field.position) <= field.radius + hero.radius;
  });
}

export function resolveHeroHealthRatio(hero: Hero): number {
  if (!Number.isFinite(hero.hp) || !Number.isFinite(hero.maxHp) || hero.maxHp <= 0) {
    return 0;
  }

  return clamp(hero.hp / hero.maxHp, 0, 1);
}

export function resolveHeroHealthVisualPlan(
  hero: Hero,
  elapsedMs: number
): HeroHealthVisualPlan {
  const healthRatio = resolveHeroHealthRatio(hero);
  const baseFillTint = resolveHeroVisual(hero.heroId).tint;
  const fillTint =
    healthRatio <= HERO_HEALTH_DANGER_RATIO
      ? HERO_HEALTH_DANGER_TINT
      : healthRatio <= HERO_HEALTH_WARNING_RATIO
      ? HERO_HEALTH_WARNING_TINT
      : baseFillTint;
  const pulseElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : 0;
  const dangerPulse = healthRatio <= HERO_HEALTH_DANGER_RATIO ? 0.04 + Math.sin(pulseElapsedMs / 170) * 0.04 : 0;

  return {
    fillWidth: HERO_HEALTH_FILL_WIDTH * healthRatio,
    fillTint,
    fillAlpha: 1,
    backgroundTint: HERO_HEALTH_BACKGROUND_TINT,
    backgroundAlpha: clamp(
      HERO_HEALTH_BACKGROUND_NORMAL_ALPHA + dangerPulse,
      HERO_HEALTH_BACKGROUND_NORMAL_ALPHA,
      1
    )
  };
}

export function resolveHeroReadabilityCreationPlan({
  hero,
  isPlayer
}: ResolveHeroReadabilityCreationPlanInput): HeroReadabilityCreationPlan {
  const visual = resolveHeroVisual(hero.heroId);
  const radius = resolveHeroReadabilityRadius(hero.radius);
  const weaponCueStyle = getWeaponCueReadabilityStyle(resolveHeroWeaponKind(hero));
  const weaponAlpha = isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha;
  const weaponStrokeAlpha = isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha;

  return {
    shadow: {
      position: hero.position,
      radius: radius * HERO_READABILITY_SHADOW_RADIUS_SCALE,
      fillColor: HERO_READABILITY_SHADOW_TINT,
      fillAlpha: isPlayer ? HERO_READABILITY_SHADOW_LOCAL_ALPHA : HERO_READABILITY_SHADOW_REMOTE_ALPHA,
      depth: HERO_READABILITY_SHADOW_DEPTH,
      visible: true
    },
    bodyDisc: {
      position: hero.position,
      radius,
      fillColor: visual.tint,
      fillAlpha: isPlayer ? HERO_READABILITY_BODY_LOCAL_ALPHA : HERO_READABILITY_BODY_REMOTE_ALPHA,
      depth: HERO_READABILITY_BODY_DEPTH,
      visible: true
    },
    silhouetteRing: {
      position: hero.position,
      radius: radius + HERO_READABILITY_SILHOUETTE_RADIUS_OFFSET,
      fillColor: HERO_READABILITY_SILHOUETTE_TINT,
      fillAlpha: HERO_READABILITY_SILHOUETTE_FILL_ALPHA,
      depth: HERO_READABILITY_SILHOUETTE_DEPTH,
      visible: true,
      stroke: {
        width: HERO_READABILITY_SILHOUETTE_STROKE_WIDTH,
        color: HERO_READABILITY_SILHOUETTE_STROKE_TINT,
        alpha: isPlayer ? HERO_READABILITY_SILHOUETTE_LOCAL_STROKE_ALPHA : HERO_READABILITY_SILHOUETTE_REMOTE_STROKE_ALPHA
      }
    },
    hitRing: {
      position: hero.position,
      radius,
      fillColor: visual.tint,
      fillAlpha: HERO_READABILITY_HIT_RING_FILL_ALPHA,
      depth: HERO_READABILITY_HIT_RING_DEPTH,
      visible: true,
      stroke: {
        width: isPlayer ? HERO_READABILITY_HIT_RING_LOCAL_STROKE_WIDTH : HERO_READABILITY_HIT_RING_REMOTE_STROKE_WIDTH,
        color: visual.tint,
        alpha: isPlayer ? HERO_READABILITY_HIT_RING_LOCAL_STROKE_ALPHA : HERO_READABILITY_HIT_RING_REMOTE_STROKE_ALPHA
      }
    },
    statusRing: {
      position: hero.position,
      radius: radius + HERO_READABILITY_STATUS_RING_RADIUS_OFFSET,
      fillColor: HERO_SLOWED_STATUS_TINT,
      fillAlpha: HERO_SLOWED_STATUS_FILL_ALPHA,
      depth: HERO_READABILITY_STATUS_RING_DEPTH,
      visible: false,
      stroke: {
        width: HERO_SLOWED_STATUS_STROKE_WIDTH,
        color: HERO_SLOWED_STATUS_TINT,
        alpha: HERO_SLOWED_STATUS_STROKE_ALPHA
      }
    },
    weaponStock: {
      position: hero.position,
      size: {
        x: radius * weaponCueStyle.stockLengthRadiusScale,
        y: weaponCueStyle.thickness * weaponCueStyle.stockThicknessScale
      },
      fillColor: weaponCueStyle.strokeTint,
      fillAlpha: weaponAlpha * weaponCueStyle.stockAlphaScale,
      depth: HERO_READABILITY_WEAPON_STOCK_DEPTH,
      visible: false,
      origin: HERO_READABILITY_WEAPON_STOCK_ORIGIN
    },
    weaponCue: {
      position: hero.position,
      size: {
        x: radius * weaponCueStyle.lengthRadiusScale,
        y: weaponCueStyle.thickness
      },
      fillColor: weaponCueStyle.tint,
      fillAlpha: weaponAlpha,
      depth: HERO_READABILITY_WEAPON_CUE_DEPTH,
      visible: false,
      origin: HERO_READABILITY_WEAPON_CUE_ORIGIN,
      stroke: {
        width: weaponCueStyle.strokeWidth,
        color: weaponCueStyle.strokeTint,
        alpha: weaponStrokeAlpha
      }
    },
    weaponMuzzle: {
      position: hero.position,
      radius: weaponCueStyle.muzzleRadius,
      fillColor: weaponCueStyle.strokeTint,
      fillAlpha: weaponAlpha * weaponCueStyle.muzzleAlphaScale,
      depth: HERO_READABILITY_WEAPON_MUZZLE_DEPTH,
      visible: false,
      stroke: {
        width: HERO_READABILITY_WEAPON_STROKE_WIDTH,
        color: weaponCueStyle.tint,
        alpha: weaponStrokeAlpha
      }
    },
    marker: isPlayer
      ? {
          position: hero.position,
          radius: radius + HERO_READABILITY_MARKER_RADIUS_OFFSET,
          fillColor: HERO_READABILITY_MARKER_FILL_TINT,
          fillAlpha: HERO_READABILITY_MARKER_FILL_ALPHA,
          depth: HERO_READABILITY_MARKER_DEPTH,
          visible: true,
          stroke: {
            width: HERO_READABILITY_MARKER_STROKE_WIDTH,
            color: HERO_READABILITY_MARKER_STROKE_TINT,
            alpha: HERO_READABILITY_MARKER_STROKE_ALPHA
          }
        }
      : null
  };
}

export function resolveHeroReadabilityVisualPlan({
  hero,
  displayPosition,
  displayFacing,
  isPlayer,
  slowFields
}: ResolveHeroReadabilityVisualPlanInput): HeroReadabilityVisualPlan {
  const radius = resolveHeroReadabilityRadius(hero.radius);
  const weaponKind = resolveHeroWeaponKind(hero);
  const weaponCueStyle = getWeaponCueReadabilityStyle(weaponKind);

  return {
    shadow: {
      position: displayPosition,
      radius: radius * HERO_READABILITY_SHADOW_RADIUS_SCALE
    },
    bodyDisc: {
      position: displayPosition,
      radius
    },
    silhouetteRing: {
      position: displayPosition,
      radius: radius + HERO_READABILITY_SILHOUETTE_RADIUS_OFFSET
    },
    hitRing: {
      position: displayPosition,
      radius
    },
    statusRing: resolveHeroReadabilityStatusRingPlan({
      visible: isHeroInsideSlowField(hero, slowFields),
      displayPosition,
      radius
    }),
    legacyWeaponCue: resolveHeroReadabilityLegacyWeaponCueVisibilityPlan(),
    weaponOverlay: {
      weaponKind,
      displayPosition,
      displayFacing,
      radius,
      cueOriginOffset: radius * HERO_READABILITY_WEAPON_CUE_ORIGIN_OFFSET_RADIUS_SCALE,
      cueLength: radius * weaponCueStyle.lengthRadiusScale,
      alpha: isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha,
      strokeAlpha: isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha
    }
  };
}

function resolveHeroReadabilityLegacyWeaponCueVisibilityPlan(): HeroReadabilityVisualPlan["legacyWeaponCue"] {
  return {
    stockVisible: false,
    cueVisible: false,
    muzzleVisible: false
  };
}

function resolveHeroReadabilityStatusRingPlan({
  visible,
  displayPosition,
  radius
}: {
  visible: boolean;
  displayPosition: Vec2;
  radius: number;
}): HeroReadabilityStatusRingVisualPlan {
  if (!visible) {
    return { visible: false };
  }

  return {
    visible: true,
    position: displayPosition,
    radius: radius + HERO_READABILITY_STATUS_RING_RADIUS_OFFSET,
    fillColor: HERO_SLOWED_STATUS_TINT,
    fillAlpha: HERO_SLOWED_STATUS_FILL_ALPHA,
    strokeWidth: HERO_SLOWED_STATUS_STROKE_WIDTH,
    strokeColor: HERO_SLOWED_STATUS_TINT,
    strokeAlpha: HERO_SLOWED_STATUS_STROKE_ALPHA
  };
}

export function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
