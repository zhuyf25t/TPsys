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
const HERO_WEAPON_MAX_DISPLAY_SIZE = 15;
const HERO_WEAPON_FORWARD_OFFSET_RADIUS_SCALE = 1.32;
const HERO_WEAPON_SIDE_OFFSET_RADIUS_SCALE = 0.9;

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
    alpha
  };
}

export function resolveHeroWeaponOverlayScale({
  frameWidth,
  frameHeight
}: ResolveHeroWeaponOverlayScaleInput): number {
  const safeFrameWidth = frameWidth > 0 ? frameWidth : HERO_WEAPON_MAX_DISPLAY_SIZE;
  const safeFrameHeight = frameHeight > 0 ? frameHeight : HERO_WEAPON_MAX_DISPLAY_SIZE;
  const sourceMax = Math.max(safeFrameWidth, safeFrameHeight, 1);
  return HERO_WEAPON_MAX_DISPLAY_SIZE / sourceMax;
}
