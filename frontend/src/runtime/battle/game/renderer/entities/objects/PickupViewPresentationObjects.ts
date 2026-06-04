import type Phaser from "phaser";
import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { WeaponTextureRef } from "../../assets/objects/BattleWeaponRasterAtlasObjects";

export interface PickupReadabilityStyle {
  radius: number;
  fillTint: number;
  fillAlpha: number;
  strokeTint: number;
  strokeAlpha: number;
  strokeWidth: number;
  spriteScale: number;
  labelColor: string;
  labelPlateTint: number;
  labelPlateAlpha: number;
  glintTint: number;
}

export interface PickupView {
  halo: Phaser.GameObjects.Arc;
  innerRing: Phaser.GameObjects.Arc;
  sprite: Phaser.GameObjects.Image;
  labelPlate: Phaser.GameObjects.Rectangle;
  glint: Phaser.GameObjects.Rectangle;
  label: Phaser.GameObjects.Text;
}

export interface CreateBasePickupViewInput {
  scene: Phaser.Scene;
  position: Vec2;
  textureKey: string;
  frameName?: WeaponTextureRef["frameName"];
  label: string;
  style: PickupReadabilityStyle;
  spriteTint?: number;
}

export interface PickupViewMotionPlan {
  bob: number;
  pulse: number;
  strokePulseAlpha: number;
  glintRotation: number;
}

export interface SyncPickupViewVisualsInput {
  view: PickupView;
  position: Vec2;
  motion: PickupViewMotionPlan;
  style: PickupReadabilityStyle;
}

export interface ResolvePickupViewCreationPlanInput {
  position: Vec2;
  textureKey: string;
  frameName?: WeaponTextureRef["frameName"];
  label: string;
  style: PickupReadabilityStyle;
  spriteTint?: number;
}

export interface PickupViewCreationPlan {
  halo: PickupCircleCreationPlan;
  innerRing: PickupCircleCreationPlan;
  sprite: PickupSpriteCreationPlan;
  labelPlate: PickupRectangleCreationPlan;
  glint: PickupRectangleCreationPlan;
  label: PickupTextCreationPlan;
}

export interface PickupCircleCreationPlan {
  position: Vec2;
  radius: number;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  stroke?: PickupStrokePlan;
}

export interface PickupRectangleCreationPlan {
  position: Vec2;
  size: Vec2;
  fillColor: number;
  fillAlpha: number;
  depth: number;
  stroke?: PickupStrokePlan;
}

export interface PickupSpriteCreationPlan {
  position: Vec2;
  textureKey: string;
  frameName?: WeaponTextureRef["frameName"];
  scale: number;
  depth: number;
  tint?: number;
}

export interface PickupTextCreationPlan {
  position: Vec2;
  label: string;
  style: Phaser.Types.GameObjects.Text.TextStyle;
  origin: Vec2;
  depth: number;
}

export interface ResolvePickupViewVisualPlanInput {
  position: Vec2;
  motion: PickupViewMotionPlan;
  style: PickupReadabilityStyle;
  labelWidth: number;
}

export interface PickupViewVisualPlan {
  halo: PickupCircleVisualPlan;
  innerRing: PickupCircleVisualPlan;
  sprite: PickupSpriteVisualPlan;
  label: PickupTextVisualPlan;
  labelPlate: PickupRectangleVisualPlan;
  glint: PickupRectangleVisualPlan;
}

export interface PickupCircleVisualPlan {
  position: Vec2;
  radius: number;
  fill?: PickupFillPlan;
  stroke?: PickupStrokePlan;
}

export interface PickupRectangleVisualPlan {
  position: Vec2;
  size?: Vec2;
  rotation?: number;
  fill?: PickupFillPlan;
  stroke?: PickupStrokePlan;
}

export interface PickupSpriteVisualPlan {
  position: Vec2;
  scale: number;
}

export interface PickupTextVisualPlan {
  position: Vec2;
}

export interface PickupFillPlan {
  color: number;
  alpha: number;
}

export interface PickupStrokePlan {
  width: number;
  color: number;
  alpha: number;
}

export const WEAPON_PICKUP_READABILITY_STYLES: Record<WeaponKind, PickupReadabilityStyle> = {
  Pistol: {
    radius: 32,
    fillTint: 0x20394b,
    fillAlpha: 0.22,
    strokeTint: 0xaeeeff,
    strokeAlpha: 0.58,
    strokeWidth: 1,
    spriteScale: 0.23,
    labelColor: "#d9f6ff",
    labelPlateTint: 0x102635,
    labelPlateAlpha: 0.76,
    glintTint: 0xcdf7ff
  },
  RocketLauncher: {
    radius: 39,
    fillTint: 0x5a2613,
    fillAlpha: 0.28,
    strokeTint: 0xff9b55,
    strokeAlpha: 0.72,
    strokeWidth: 2,
    spriteScale: 0.26,
    labelColor: "#ffd7ad",
    labelPlateTint: 0x32170e,
    labelPlateAlpha: 0.78,
    glintTint: 0xffe0a8
  },
  Gatling: {
    radius: 36,
    fillTint: 0x4b3415,
    fillAlpha: 0.25,
    strokeTint: 0xffd86d,
    strokeAlpha: 0.68,
    strokeWidth: 2,
    spriteScale: 0.26,
    labelColor: "#ffe7a3",
    labelPlateTint: 0x2d220f,
    labelPlateAlpha: 0.78,
    glintTint: 0xfff0a3
  },
  Shotgun: {
    radius: 37,
    fillTint: 0x52311b,
    fillAlpha: 0.26,
    strokeTint: 0xffefb7,
    strokeAlpha: 0.68,
    strokeWidth: 2,
    spriteScale: 0.26,
    labelColor: "#fff0ce",
    labelPlateTint: 0x2f1c10,
    labelPlateAlpha: 0.78,
    glintTint: 0xfff4cd
  }
};

export const ITEM_PICKUP_READABILITY_STYLE: PickupReadabilityStyle = {
  radius: 35,
  fillTint: 0x183c23,
  fillAlpha: 0.24,
  strokeTint: 0x7bff9b,
  strokeAlpha: 0.7,
  strokeWidth: 2,
  spriteScale: 0.72,
  labelColor: "#d8ffe1",
  labelPlateTint: 0x102719,
  labelPlateAlpha: 0.78,
  glintTint: 0xc9ffd4
};
