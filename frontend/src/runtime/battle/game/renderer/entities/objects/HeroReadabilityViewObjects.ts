import type Phaser from "phaser";
import type { BattleSlowFieldState as SlowField } from "../../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { HeroWeaponOverlayView } from "./HeroWeaponOverlayObjects";

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

export interface HeroHealthVisualPlan {
  fillWidth: number;
  fillTint: number;
  fillAlpha: number;
  backgroundTint: number;
  backgroundAlpha: number;
}

export interface ResolveHeroReadabilityCreationPlanInput {
  hero: Hero;
  isPlayer: boolean;
}

export interface HeroReadabilityCreationPlan {
  shadow: HeroReadabilityCircleCreationPlan;
  bodyDisc: HeroReadabilityCircleCreationPlan;
  silhouetteRing: HeroReadabilityCircleCreationPlan;
  hitRing: HeroReadabilityCircleCreationPlan;
  statusRing: HeroReadabilityCircleCreationPlan;
  weaponStock: HeroReadabilityRectangleCreationPlan;
  weaponCue: HeroReadabilityRectangleCreationPlan;
  weaponMuzzle: HeroReadabilityCircleCreationPlan;
  marker: HeroReadabilityCircleCreationPlan | null;
}

export interface HeroReadabilityCircleCreationPlan {
  position: Vec2;
  radius: number;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  visible: boolean;
  stroke?: HeroReadabilityStrokeCreationPlan;
}

export interface HeroReadabilityRectangleCreationPlan {
  position: Vec2;
  size: Vec2;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  visible: boolean;
  origin?: Vec2;
  stroke?: HeroReadabilityStrokeCreationPlan;
}

export interface HeroReadabilityStrokeCreationPlan {
  width: number;
  color: number;
  alpha: number;
}

export interface ResolveHeroReadabilityVisualPlanInput {
  hero: Hero;
  displayPosition: Vec2;
  displayFacing: number;
  isPlayer: boolean;
  slowFields: readonly SlowField[];
}

export interface HeroReadabilityVisualPlan {
  shadow: HeroReadabilityCircleVisualPlan;
  bodyDisc: HeroReadabilityCircleVisualPlan;
  silhouetteRing: HeroReadabilityCircleVisualPlan;
  hitRing: HeroReadabilityCircleVisualPlan;
  statusRing: HeroReadabilityStatusRingVisualPlan;
  legacyWeaponCue: HeroReadabilityLegacyWeaponCueVisibilityPlan;
  weaponOverlay: HeroReadabilityWeaponOverlayVisualPlan;
}

export interface HeroReadabilityCircleVisualPlan {
  position: Vec2;
  radius: number;
}

export type HeroReadabilityStatusRingVisualPlan =
  | {
      visible: false;
    }
  | {
      visible: true;
      position: Vec2;
      radius: number;
      fillColor: number;
      fillAlpha: number;
      strokeWidth: number;
      strokeColor: number;
      strokeAlpha: number;
    };

export interface HeroReadabilityLegacyWeaponCueVisibilityPlan {
  stockVisible: boolean;
  cueVisible: boolean;
  muzzleVisible: boolean;
}

export interface HeroReadabilityWeaponOverlayVisualPlan {
  weaponKind: WeaponKind;
  displayPosition: Vec2;
  displayFacing: number;
  radius: number;
  cueOriginOffset: number;
  cueLength: number;
  alpha: number;
  strokeAlpha: number;
}
