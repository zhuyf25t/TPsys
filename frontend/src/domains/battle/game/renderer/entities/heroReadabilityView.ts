import Phaser from "phaser";
import type { Hero, SlowField, Vec2, WeaponKind } from "../../../objects/types";
import { resolveHeroVisual } from "../../spawn";
import { syncHeroWeaponOverlayVisuals, type HeroWeaponOverlayView } from "./heroWeaponOverlayView";

const HERO_READABILITY_MIN_RADIUS = 18;
const HERO_READABILITY_MARKER_DEPTH = 32;
const HERO_READABILITY_SHADOW_DEPTH = 33;
const HERO_READABILITY_BODY_DEPTH = 34;
const HERO_READABILITY_SILHOUETTE_DEPTH = 35;
const HERO_READABILITY_HIT_RING_DEPTH = 36;
const HERO_READABILITY_STATUS_RING_DEPTH = 37;
const HERO_READABILITY_WEAPON_STOCK_DEPTH = 38;
const HERO_READABILITY_WEAPON_CUE_DEPTH = 39;
const HERO_READABILITY_WEAPON_MUZZLE_DEPTH = 40;
const HERO_HEALTH_WARNING_RATIO = 0.55;
const HERO_HEALTH_DANGER_RATIO = 0.3;
const HERO_HEALTH_WARNING_TINT = 0xffc857;
const HERO_HEALTH_DANGER_TINT = 0xff5a4f;
const HERO_HEALTH_BACKGROUND_TINT = 0x0d1014;
const HERO_HEALTH_BACKGROUND_NORMAL_ALPHA = 0.95;
const HERO_SLOWED_STATUS_TINT = 0x9bf8ff;
const HERO_SLOWED_STATUS_FILL_ALPHA = 0.055;
const HERO_SLOWED_STATUS_STROKE_ALPHA = 0.58;

export interface WeaponCueReadabilityStyle {
  lengthRadiusScale: number;
  thickness: number;
  tint: number;
  localAlpha: number;
  remoteAlpha: number;
  strokeWidth: number;
  strokeTint: number;
  localStrokeAlpha: number;
  remoteStrokeAlpha: number;
  stockLengthRadiusScale: number;
  stockThicknessScale: number;
  stockAlphaScale: number;
  muzzleRadius: number;
  muzzleAlphaScale: number;
}

export const WEAPON_CUE_READABILITY_STYLES: Record<WeaponKind, WeaponCueReadabilityStyle> = {
  Pistol: {
    lengthRadiusScale: 0.68,
    thickness: 4,
    tint: 0xfff0c6,
    localAlpha: 0.68,
    remoteAlpha: 0.46,
    strokeWidth: 1,
    strokeTint: 0xfff7df,
    localStrokeAlpha: 0.24,
    remoteStrokeAlpha: 0.14,
    stockLengthRadiusScale: 0.34,
    stockThicknessScale: 1.25,
    stockAlphaScale: 0.68,
    muzzleRadius: 3,
    muzzleAlphaScale: 0.78
  },
  RocketLauncher: {
    lengthRadiusScale: 1.38,
    thickness: 9,
    tint: 0xff9b55,
    localAlpha: 0.86,
    remoteAlpha: 0.68,
    strokeWidth: 2,
    strokeTint: 0xffd2a8,
    localStrokeAlpha: 0.38,
    remoteStrokeAlpha: 0.24,
    stockLengthRadiusScale: 0.62,
    stockThicknessScale: 1.5,
    stockAlphaScale: 0.7,
    muzzleRadius: 7,
    muzzleAlphaScale: 0.82
  },
  Gatling: {
    lengthRadiusScale: 1.22,
    thickness: 5,
    tint: 0xffd86d,
    localAlpha: 0.84,
    remoteAlpha: 0.64,
    strokeWidth: 1,
    strokeTint: 0xffefaa,
    localStrokeAlpha: 0.34,
    remoteStrokeAlpha: 0.2,
    stockLengthRadiusScale: 0.5,
    stockThicknessScale: 1.15,
    stockAlphaScale: 0.66,
    muzzleRadius: 5,
    muzzleAlphaScale: 0.8
  },
  Shotgun: {
    lengthRadiusScale: 1.04,
    thickness: 11,
    tint: 0xffefb7,
    localAlpha: 0.82,
    remoteAlpha: 0.62,
    strokeWidth: 2,
    strokeTint: 0xfff7d6,
    localStrokeAlpha: 0.34,
    remoteStrokeAlpha: 0.22,
    stockLengthRadiusScale: 0.72,
    stockThicknessScale: 1.35,
    stockAlphaScale: 0.72,
    muzzleRadius: 5,
    muzzleAlphaScale: 0.78
  }
};

export interface HeroReadabilityView {
  shadow: Phaser.GameObjects.Arc;
  bodyDisc: Phaser.GameObjects.Arc;
  silhouetteRing: Phaser.GameObjects.Arc;
  hitRing: Phaser.GameObjects.Arc;
  statusRing: Phaser.GameObjects.Arc;
  weaponStock: Phaser.GameObjects.Rectangle;
  weaponCue: Phaser.GameObjects.Rectangle;
  weaponMuzzle: Phaser.GameObjects.Arc;
  marker: Phaser.GameObjects.Arc | null;
}

export interface HeroReadabilitySyncView extends HeroReadabilityView {
  weaponOverlay: HeroWeaponOverlayView;
}

export interface HeroHealthView {
  healthBackground: Phaser.GameObjects.Rectangle;
  healthFill: Phaser.GameObjects.Rectangle;
}

/** 中文名：创建英雄readabilityview（createHeroReadabilityView）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createHeroReadabilityView(scene: Phaser.Scene, hero: Hero, isPlayer: boolean): HeroReadabilityView {
  const visual = resolveHeroVisual(hero.heroId);
  const readabilityRadius = resolveHeroReadabilityRadius(hero.radius);
  const shadow = scene.add
    .circle(hero.position.x, hero.position.y, readabilityRadius * 1.08, 0x020711, isPlayer ? 0.3 : 0.22)
    .setDepth(HERO_READABILITY_SHADOW_DEPTH);
  const bodyDisc = scene.add
    .circle(hero.position.x, hero.position.y, readabilityRadius, visual.tint, isPlayer ? 0.15 : 0.1)
    .setDepth(HERO_READABILITY_BODY_DEPTH);
  const silhouetteRing = scene.add
    .circle(hero.position.x, hero.position.y, readabilityRadius + 1, 0x000000, 0)
    .setDepth(HERO_READABILITY_SILHOUETTE_DEPTH);
  silhouetteRing.setStrokeStyle(3, 0x06101b, isPlayer ? 0.58 : 0.42);
  const hitRing = scene.add.circle(hero.position.x, hero.position.y, readabilityRadius, visual.tint, 0).setDepth(HERO_READABILITY_HIT_RING_DEPTH);
  hitRing.setStrokeStyle(isPlayer ? 2 : 1, visual.tint, isPlayer ? 0.62 : 0.36);
  const statusRing = scene.add
    .circle(hero.position.x, hero.position.y, readabilityRadius + 6, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_FILL_ALPHA)
    .setDepth(HERO_READABILITY_STATUS_RING_DEPTH)
    .setVisible(false);
  statusRing.setStrokeStyle(2, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_STROKE_ALPHA);

  const weaponCueStyle = getWeaponCueReadabilityStyle(resolveHeroWeaponKind(hero));
  const weaponStock = scene.add
    .rectangle(
      hero.position.x,
      hero.position.y,
      readabilityRadius * weaponCueStyle.stockLengthRadiusScale,
      weaponCueStyle.thickness * weaponCueStyle.stockThicknessScale,
      weaponCueStyle.strokeTint,
      (isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha) * weaponCueStyle.stockAlphaScale
    )
    .setOrigin(0.74, 0.5)
    .setDepth(HERO_READABILITY_WEAPON_STOCK_DEPTH);
  const weaponCue = scene.add
    .rectangle(
      hero.position.x,
      hero.position.y,
      readabilityRadius * weaponCueStyle.lengthRadiusScale,
      weaponCueStyle.thickness,
      weaponCueStyle.tint,
      isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha
    )
    .setOrigin(0.08, 0.5)
    .setDepth(HERO_READABILITY_WEAPON_CUE_DEPTH);
  weaponCue.setStrokeStyle(weaponCueStyle.strokeWidth, weaponCueStyle.strokeTint, isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha);
  const weaponMuzzle = scene.add
    .circle(
      hero.position.x,
      hero.position.y,
      weaponCueStyle.muzzleRadius,
      weaponCueStyle.strokeTint,
      (isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha) * weaponCueStyle.muzzleAlphaScale
    )
    .setDepth(HERO_READABILITY_WEAPON_MUZZLE_DEPTH);
  weaponMuzzle.setStrokeStyle(1, weaponCueStyle.tint, isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha);

  const marker =
    isPlayer
      ? scene.add.circle(hero.position.x, hero.position.y, readabilityRadius + 8, 0x4ad9ff, 0.035).setDepth(HERO_READABILITY_MARKER_DEPTH)
      : null;

  if (marker) {
    marker.setStrokeStyle(2, 0x76e4ff, 0.55);
  }

  return {
    shadow,
    bodyDisc,
    silhouetteRing,
    hitRing,
    statusRing,
    weaponStock,
    weaponCue,
    weaponMuzzle,
    marker
  };
}

/** 中文名：解析英雄readabilityradius（resolveHeroReadabilityRadius）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveHeroReadabilityRadius(radius: number): number {
  return Math.max(HERO_READABILITY_MIN_RADIUS, Number.isFinite(radius) ? radius : HERO_READABILITY_MIN_RADIUS);
}

/** 中文名：解析英雄武器kind（resolveHeroWeaponKind）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveHeroWeaponKind(hero: Hero): WeaponKind {
  return hero.weapons[hero.currentWeaponIndex]?.weaponKind ?? "Pistol";
}

/** 中文名：获取武器cuereadabilitystyle（getWeaponCueReadabilityStyle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getWeaponCueReadabilityStyle(weaponKind: WeaponKind): WeaponCueReadabilityStyle {
  return WEAPON_CUE_READABILITY_STYLES[weaponKind];
}

/** 中文名：sync英雄readabilityvisuals（syncHeroReadabilityVisuals）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncHeroReadabilityVisuals(
  view: HeroReadabilitySyncView,
  hero: Hero,
  displayPosition: Vec2,
  displayFacing: number,
  isPlayer: boolean,
  slowFields: readonly SlowField[]
): void {
  const radius = resolveHeroReadabilityRadius(hero.radius);
  view.shadow.setPosition(displayPosition.x, displayPosition.y);
  view.shadow.setRadius(radius * 1.08);
  view.bodyDisc.setPosition(displayPosition.x, displayPosition.y);
  view.bodyDisc.setRadius(radius);
  view.silhouetteRing.setPosition(displayPosition.x, displayPosition.y);
  view.silhouetteRing.setRadius(radius + 1);
  view.hitRing.setPosition(displayPosition.x, displayPosition.y);
  view.hitRing.setRadius(radius);
  const slowed = isHeroInsideSlowField(hero, slowFields);
  view.statusRing.setVisible(slowed);
  if (slowed) {
    view.statusRing.setPosition(displayPosition.x, displayPosition.y);
    view.statusRing.setRadius(radius + 6);
    view.statusRing.setFillStyle(HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_FILL_ALPHA);
    view.statusRing.setStrokeStyle(2, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_STROKE_ALPHA);
  }
  const weaponKind = resolveHeroWeaponKind(hero);
  const weaponCueStyle = getWeaponCueReadabilityStyle(weaponKind);
  const directionX = Math.cos(displayFacing);
  const directionY = Math.sin(displayFacing);
  const cueLength = radius * weaponCueStyle.lengthRadiusScale;
  const cueOriginOffset = radius * 0.22;
  const alpha = isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha;
  const strokeAlpha = isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha;

  view.weaponStock.setPosition(displayPosition.x - directionX * radius * 0.05, displayPosition.y - directionY * radius * 0.05);
  view.weaponStock.setRotation(displayFacing);
  view.weaponStock.setDisplaySize(radius * weaponCueStyle.stockLengthRadiusScale, weaponCueStyle.thickness * weaponCueStyle.stockThicknessScale);
  view.weaponStock.setFillStyle(weaponCueStyle.strokeTint, alpha * weaponCueStyle.stockAlphaScale);
  view.weaponStock.setStrokeStyle(1, weaponCueStyle.tint, strokeAlpha * 0.72);

  view.weaponCue.setPosition(displayPosition.x + directionX * cueOriginOffset, displayPosition.y + directionY * cueOriginOffset);
  view.weaponCue.setRotation(displayFacing);
  view.weaponCue.setDisplaySize(cueLength, weaponCueStyle.thickness);
  view.weaponCue.setFillStyle(weaponCueStyle.tint, alpha);
  view.weaponCue.setStrokeStyle(
    weaponCueStyle.strokeWidth,
    weaponCueStyle.strokeTint,
    strokeAlpha
  );

  view.weaponMuzzle.setPosition(
    displayPosition.x + directionX * (cueOriginOffset + cueLength * 0.92),
    displayPosition.y + directionY * (cueOriginOffset + cueLength * 0.92)
  );
  view.weaponMuzzle.setRadius(weaponCueStyle.muzzleRadius);
  view.weaponMuzzle.setFillStyle(weaponCueStyle.strokeTint, alpha * weaponCueStyle.muzzleAlphaScale);
  view.weaponMuzzle.setStrokeStyle(1, weaponCueStyle.tint, strokeAlpha);
  syncHeroWeaponOverlayVisuals({
    view: view.weaponOverlay,
    weaponKind,
    displayPosition,
    displayFacing,
    radius,
    cueOriginOffset,
    cueLength,
    alpha,
    strokeAlpha
  });
}

/** 中文名：判断是否英雄inside减速field（isHeroInsideSlowField）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isHeroInsideSlowField(hero: Hero, slowFields: readonly SlowField[]): boolean {
  if (!isFiniteVec2(hero.position)) {
    return false;
  }

  return slowFields.some((field) => {
    if (!isFiniteVec2(field.position) || !Number.isFinite(field.radius) || field.radius <= 0) {
      return false;
    }

    return Phaser.Math.Distance.Between(hero.position.x, hero.position.y, field.position.x, field.position.y) <= field.radius + hero.radius;
  });
}

/** 中文名：sync英雄healthvisuals（syncHeroHealthVisuals）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncHeroHealthVisuals(view: HeroHealthView, hero: Hero, elapsedMs: number): void {
  const healthRatio = resolveHeroHealthRatio(hero);
  const visual = resolveHeroVisual(hero.heroId);
  const fillTint =
    healthRatio <= HERO_HEALTH_DANGER_RATIO
      ? HERO_HEALTH_DANGER_TINT
      : healthRatio <= HERO_HEALTH_WARNING_RATIO
      ? HERO_HEALTH_WARNING_TINT
      : visual.tint;
  const pulseElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : 0;
  const dangerPulse = healthRatio <= HERO_HEALTH_DANGER_RATIO ? 0.04 + Math.sin(pulseElapsedMs / 170) * 0.04 : 0;
  const backgroundAlpha = Phaser.Math.Clamp(
    HERO_HEALTH_BACKGROUND_NORMAL_ALPHA + dangerPulse,
    HERO_HEALTH_BACKGROUND_NORMAL_ALPHA,
    1
  );

  view.healthFill.displayWidth = 48 * healthRatio;
  view.healthFill.setFillStyle(fillTint, 1);
  view.healthBackground.setFillStyle(HERO_HEALTH_BACKGROUND_TINT, backgroundAlpha);
}

/** 中文名：解析英雄healthratio（resolveHeroHealthRatio）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveHeroHealthRatio(hero: Hero): number {
  if (!Number.isFinite(hero.hp) || !Number.isFinite(hero.maxHp) || hero.maxHp <= 0) {
    return 0;
  }

  return Phaser.Math.Clamp(hero.hp / hero.maxHp, 0, 1);
}

/** 中文名：判断是否finitevec2（isFiniteVec2）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}
