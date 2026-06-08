import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import { getWeaponWorldTextureRef } from "../../assets/BattleWeaponRasterAtlas";
import type {
  HeroWeaponOverlayCreationPlan,
  HeroWeaponOverlayLayoutPlan,
  HeroWeaponOverlayTexturePlan,
  HeroWeaponOverlayVisualPlan,
  ResolveHeroWeaponOverlayCreationPlanInput,
  ResolveHeroWeaponOverlayLayoutPlanInput,
  ResolveHeroWeaponOverlayScaleInput,
  ResolveHeroWeaponOverlayTexturePlanInput,
  ResolveHeroWeaponOverlayVisualPlanInput
} from "../objects/HeroWeaponOverlayObjects";

const HERO_WEAPON_OVERLAY_DEFAULT_KIND: WeaponKind = "Pistol";
const HERO_WEAPON_OVERLAY_DEPTH = 52;
const HERO_WEAPON_OVERLAY_ORIGIN = { x: 0.5, y: 0.5 } as const;
const HERO_WEAPON_MIN_DISPLAY_SIZE = 28;
const HERO_WEAPON_MAX_DISPLAY_SIZE = 58;
const HERO_WEAPON_FORWARD_OFFSET_RADIUS_SCALE = 1.32;
const HERO_WEAPON_SIDE_OFFSET_RADIUS_SCALE = 0.9;
const HERO_WEAPON_DISPLAY_RADIUS_SCALE: Readonly<Record<WeaponKind, number>> = {
  Pistol: 1.7,
  Shotgun: 2.15,
  Gatling: 2.5,
  RocketLauncher: 2.65
};

export function resolveHeroWeaponOverlayCreationPlan({
  position
}: ResolveHeroWeaponOverlayCreationPlanInput): HeroWeaponOverlayCreationPlan {
  return {
    position,
    ...resolveHeroWeaponOverlayTexturePlan({
      weaponKind: HERO_WEAPON_OVERLAY_DEFAULT_KIND
    }),
    origin: HERO_WEAPON_OVERLAY_ORIGIN,
    depth: HERO_WEAPON_OVERLAY_DEPTH,
    visible: false
  };
}

export function resolveHeroWeaponOverlayTexturePlan({
  weaponKind
}: ResolveHeroWeaponOverlayTexturePlanInput): HeroWeaponOverlayTexturePlan {
  const textureRef = getWeaponWorldTextureRef(weaponKind);

  return {
    textureKey: textureRef.textureKey,
    frameName: textureRef.frameName
  };
}

export function resolveHeroWeaponOverlayLayoutPlan({
  displayPosition,
  displayFacing,
  radius
}: ResolveHeroWeaponOverlayLayoutPlanInput): HeroWeaponOverlayLayoutPlan {
  const directionX = Math.cos(displayFacing);
  const directionY = Math.sin(displayFacing);
  const perpendicularX = -directionY;
  const perpendicularY = directionX;
  const forwardOffset = radius * HERO_WEAPON_FORWARD_OFFSET_RADIUS_SCALE;
  const sideOffset = radius * HERO_WEAPON_SIDE_OFFSET_RADIUS_SCALE;

  return {
    position: {
      x: displayPosition.x + directionX * forwardOffset + perpendicularX * sideOffset,
      y: displayPosition.y + directionY * forwardOffset + perpendicularY * sideOffset
    },
    rotation: displayFacing
  };
}

export function resolveHeroWeaponOverlayVisualPlan({
  weaponKind,
  displayPosition,
  displayFacing,
  radius,
  alpha
}: ResolveHeroWeaponOverlayVisualPlanInput): HeroWeaponOverlayVisualPlan {
  return {
    ...resolveHeroWeaponOverlayTexturePlan({ weaponKind }),
    ...resolveHeroWeaponOverlayLayoutPlan({
      displayPosition,
      displayFacing,
      radius
    }),
    visible: true,
    alpha,
    displaySize: resolveHeroWeaponOverlayDisplaySize({ weaponKind, radius })
  };
}

export function resolveHeroWeaponOverlayScale({
  frameWidth,
  frameHeight,
  displaySize
}: ResolveHeroWeaponOverlayScaleInput): number {
  const safeDisplaySize = Number.isFinite(displaySize)
    ? clamp(displaySize, HERO_WEAPON_MIN_DISPLAY_SIZE, HERO_WEAPON_MAX_DISPLAY_SIZE)
    : HERO_WEAPON_MIN_DISPLAY_SIZE;
  const safeFrameWidth = frameWidth > 0 ? frameWidth : safeDisplaySize;
  const safeFrameHeight = frameHeight > 0 ? frameHeight : safeDisplaySize;
  const sourceMax = Math.max(safeFrameWidth, safeFrameHeight, 1);
  return safeDisplaySize / sourceMax;
}

function resolveHeroWeaponOverlayDisplaySize({
  weaponKind,
  radius
}: {
  weaponKind: WeaponKind;
  radius: number;
}): number {
  const safeRadius = Number.isFinite(radius) && radius > 0 ? radius : HERO_WEAPON_MIN_DISPLAY_SIZE;
  return clamp(
    safeRadius * HERO_WEAPON_DISPLAY_RADIUS_SCALE[weaponKind],
    HERO_WEAPON_MIN_DISPLAY_SIZE,
    HERO_WEAPON_MAX_DISPLAY_SIZE
  );
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
