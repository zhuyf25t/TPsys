import Phaser from "phaser";
import type { Vec2, WeaponKind } from "../../../../../objects/battle/types";
import { getWeaponWorldTextureRef } from "../weaponRasterAtlas";

const HERO_READABILITY_WEAPON_OVERLAY_DEPTH = 52;
const HERO_WEAPON_MAX_DISPLAY_SIZE = 30;
const HERO_WEAPON_FORWARD_OFFSET_RADIUS_SCALE = 1.32;
const HERO_WEAPON_SIDE_OFFSET_RADIUS_SCALE = 0.9;

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

/** 中文名：创建英雄武器贴图层（createHeroWeaponOverlayView）。游戏职责：创建角色身上的持枪 world 贴图容器，由同步函数按武器类型切换真实 SVG。 */
export function createHeroWeaponOverlayView(scene: Phaser.Scene, position: Vec2): HeroWeaponOverlayView {
  const textureRef = getWeaponWorldTextureRef("Pistol");
  return {
    sprite: scene.add
      .image(position.x, position.y, textureRef.textureKey, textureRef.frameName)
      .setOrigin(0.5, 0.5)
      .setDepth(HERO_READABILITY_WEAPON_OVERLAY_DEPTH)
      .setVisible(false),
    textureKey: textureRef.textureKey,
    frameName: textureRef.frameName
  };
}

/** 中文名：设置英雄武器贴图可见（setHeroWeaponOverlayVisible）。游戏职责：在角色死亡、离屏或重用视图时统一隐藏/显示持枪贴图。 */
export function setHeroWeaponOverlayVisible(view: HeroWeaponOverlayView, visible: boolean): void {
  view.sprite.setVisible(visible);
}

/** 中文名：同步英雄武器贴图视觉（syncHeroWeaponOverlayVisuals）。游戏职责：把当前武器的 world SVG 固定在角色中下方，并让它随角色朝向旋转。 */
export function syncHeroWeaponOverlayVisuals(input: SyncHeroWeaponOverlayVisualsInput): void {
  const { view, weaponKind, displayPosition, displayFacing, radius, alpha } = input;
  const textureRef = getWeaponWorldTextureRef(weaponKind);
  const directionX = Math.cos(displayFacing);
  const directionY = Math.sin(displayFacing);
  const perpendicularX = -directionY;
  const perpendicularY = directionX;
  const forwardOffset = radius * HERO_WEAPON_FORWARD_OFFSET_RADIUS_SCALE;
  const sideOffset = radius * HERO_WEAPON_SIDE_OFFSET_RADIUS_SCALE;

  if (
    view.textureKey !== textureRef.textureKey ||
    view.frameName !== textureRef.frameName ||
    view.sprite.texture.key !== textureRef.textureKey
  ) {
    view.sprite.setTexture(textureRef.textureKey, textureRef.frameName);
    view.textureKey = textureRef.textureKey;
    view.frameName = textureRef.frameName;
    syncWeaponSpriteDisplayScale(view.sprite);
  }

  view.sprite.setVisible(true);
  view.sprite.setAlpha(alpha);
  view.sprite.setPosition(
    displayPosition.x + directionX * forwardOffset + perpendicularX * sideOffset,
    displayPosition.y + directionY * forwardOffset + perpendicularY * sideOffset
  );
  view.sprite.setRotation(displayFacing);
}

function syncWeaponSpriteDisplayScale(sprite: Phaser.GameObjects.Image): void {
  const frameWidth = sprite.frame.width > 0 ? sprite.frame.width : HERO_WEAPON_MAX_DISPLAY_SIZE;
  const frameHeight = sprite.frame.height > 0 ? sprite.frame.height : HERO_WEAPON_MAX_DISPLAY_SIZE;
  const sourceMax = Math.max(frameWidth, frameHeight, 1);
  sprite.setScale(HERO_WEAPON_MAX_DISPLAY_SIZE / sourceMax);
}
