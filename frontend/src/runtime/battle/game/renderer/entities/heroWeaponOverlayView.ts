import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type Phaser from "phaser";
import {
  resolveHeroWeaponOverlayCreationPlan,
  resolveHeroWeaponOverlayScale,
  resolveHeroWeaponOverlayVisualPlan
} from "./functions/HeroWeaponOverlayRules";
import type {
  HeroWeaponOverlayVisualPlan,
  HeroWeaponOverlayView,
  SyncHeroWeaponOverlayVisualsInput
} from "./objects/HeroWeaponOverlayObjects";

export type {
  HeroWeaponOverlayView,
  SyncHeroWeaponOverlayVisualsInput
} from "./objects/HeroWeaponOverlayObjects";

/** 中文名：创建英雄武器贴图层（createHeroWeaponOverlayView）。游戏职责：创建角色身上的持枪 world 贴图容器，由同步函数按武器类型切换真实 SVG。 */
export function createHeroWeaponOverlayView(scene: Phaser.Scene, position: Vec2): HeroWeaponOverlayView {
  const plan = resolveHeroWeaponOverlayCreationPlan({ position });
  return {
    sprite: scene.add
      .image(plan.position.x, plan.position.y, plan.textureKey, plan.frameName)
      .setOrigin(plan.origin.x, plan.origin.y)
      .setDepth(plan.depth)
      .setVisible(plan.visible),
    textureKey: plan.textureKey,
    frameName: plan.frameName
  };
}

/** 中文名：设置英雄武器贴图可见（setHeroWeaponOverlayVisible）。游戏职责：在角色死亡、离屏或重用视图时统一隐藏/显示持枪贴图。 */
export function setHeroWeaponOverlayVisible(view: HeroWeaponOverlayView, visible: boolean): void {
  view.sprite.setVisible(visible);
}

/** 中文名：同步英雄武器贴图视觉（syncHeroWeaponOverlayVisuals）。游戏职责：把当前武器的 world SVG 固定在角色中下方，并让它随角色朝向旋转。 */
export function syncHeroWeaponOverlayVisuals(input: SyncHeroWeaponOverlayVisualsInput): void {
  const { view, weaponKind, displayPosition, displayFacing, radius, alpha } = input;
  applyHeroWeaponOverlayVisualPlan(view, resolveHeroWeaponOverlayVisualPlan({
    weaponKind,
    displayPosition,
    displayFacing,
    radius,
    alpha
  }));
}

function applyHeroWeaponOverlayVisualPlan(
  view: HeroWeaponOverlayView,
  plan: HeroWeaponOverlayVisualPlan
): void {
  if (
    view.textureKey !== plan.textureKey ||
    view.frameName !== plan.frameName ||
    view.sprite.texture.key !== plan.textureKey
  ) {
    view.sprite.setTexture(plan.textureKey, plan.frameName);
    view.textureKey = plan.textureKey;
    view.frameName = plan.frameName;
  }

  syncWeaponSpriteDisplayScale(view.sprite, plan.displaySize);
  view.sprite.setVisible(plan.visible);
  view.sprite.setAlpha(plan.alpha);
  view.sprite.setPosition(plan.position.x, plan.position.y);
  view.sprite.setRotation(plan.rotation);
}

function syncWeaponSpriteDisplayScale(sprite: Phaser.GameObjects.Image, displaySize: number): void {
  sprite.setScale(
    resolveHeroWeaponOverlayScale({
      frameWidth: sprite.frame.width,
      frameHeight: sprite.frame.height,
      displaySize
    })
  );
}
