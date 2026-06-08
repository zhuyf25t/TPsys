import type Phaser from "phaser";
import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { WeaponTextureRef } from "../../assets/objects/BattleWeaponRasterAtlasObjects";

export interface HeroWeaponOverlayView {
  sprite: Phaser.GameObjects.Image;
  textureKey: string;
  frameName?: string;
}

export interface SyncHeroWeaponOverlayVisualsInput {
  view: HeroWeaponOverlayView;
  weaponKind: WeaponKind;
  displayPosition: Vec2;
  displayFacing: number;
  radius: number;
  cueOriginOffset: number;
  cueLength: number;
  alpha: number;
  strokeAlpha: number;
}

export interface ResolveHeroWeaponOverlayCreationPlanInput {
  position: Vec2;
}

export interface HeroWeaponOverlayCreationPlan extends HeroWeaponOverlayTexturePlan {
  position: Vec2;
  origin: Vec2;
  depth: number;
  visible: boolean;
}

export interface ResolveHeroWeaponOverlayTexturePlanInput {
  weaponKind: WeaponKind;
}

export interface HeroWeaponOverlayTexturePlan {
  textureKey: WeaponTextureRef["textureKey"];
  frameName?: WeaponTextureRef["frameName"];
}

export interface ResolveHeroWeaponOverlayLayoutPlanInput {
  displayPosition: Vec2;
  displayFacing: number;
  radius: number;
}

export interface HeroWeaponOverlayLayoutPlan {
  position: Vec2;
  rotation: number;
}

export interface ResolveHeroWeaponOverlayVisualPlanInput {
  weaponKind: WeaponKind;
  displayPosition: Vec2;
  displayFacing: number;
  radius: number;
  alpha: number;
}

export interface HeroWeaponOverlayVisualPlan extends HeroWeaponOverlayTexturePlan, HeroWeaponOverlayLayoutPlan {
  visible: boolean;
  alpha: number;
  displaySize: number;
}

export interface ResolveHeroWeaponOverlayScaleInput {
  frameWidth: number;
  frameHeight: number;
  displaySize: number;
}
