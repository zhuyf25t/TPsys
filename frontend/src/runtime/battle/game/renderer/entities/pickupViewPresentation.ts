import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import Phaser from "phaser";
import { CRATE_TEXTURE_KEY } from "../../objects/BattleGameConstants";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel } from "../../presenters/battleDisplayCatalog";
import { getWeaponPickupTextureRef } from "../assets/BattleWeaponRasterAtlas";
import {
  getItemPickupReadabilityStyle,
  getWeaponPickupReadabilityStyle,
  resolveItemPickupSpriteTint,
  resolvePickupViewCreationPlan,
  resolvePickupViewVisualPlan,
  resolveItemPickupMotionPlan,
  resolveWeaponPickupMotionPlan
} from "./functions/PickupViewPresentationRules";
import type {
  CreateBasePickupViewInput,
  PickupCircleCreationPlan,
  PickupCircleVisualPlan,
  PickupRectangleCreationPlan,
  PickupRectangleVisualPlan,
  PickupSpriteCreationPlan,
  PickupSpriteVisualPlan,
  PickupTextCreationPlan,
  PickupTextVisualPlan,
  PickupView,
  SyncPickupViewVisualsInput
} from "./objects/PickupViewPresentationObjects";

export type {
  CreateBasePickupViewInput,
  PickupReadabilityStyle,
  PickupView,
  PickupViewMotionPlan,
  SyncPickupViewVisualsInput
} from "./objects/PickupViewPresentationObjects";

export function createWeaponPickupView(scene: Phaser.Scene, pickup: WeaponPickup): PickupView {
  const style = getWeaponPickupReadabilityStyle(pickup.weaponKind);
  const textureRef = getWeaponPickupTextureRef(pickup.weaponKind);
  return createBasePickupView({
    scene,
    position: pickup.position,
    textureKey: textureRef.textureKey,
    frameName: textureRef.frameName,
    label: getWeaponDisplayLabel(pickup.weaponKind),
    style
  });
}

export function createItemPickupView(scene: Phaser.Scene, pickup: ItemPickup): PickupView {
  const style = getItemPickupReadabilityStyle();
  return createBasePickupView({
    scene,
    position: pickup.position,
    textureKey: CRATE_TEXTURE_KEY,
    label: getItemPickupDisplayLabel(pickup.kind),
    style,
    spriteTint: resolveItemPickupSpriteTint()
  });
}

export function setPickupViewVisible(view: PickupView, visible: boolean): void {
  view.halo.setVisible(visible);
  view.innerRing.setVisible(visible);
  view.sprite.setVisible(visible);
  view.labelPlate.setVisible(visible);
  view.glint.setVisible(visible);
  view.label.setVisible(visible);
}

export function syncWeaponPickupView(view: PickupView, pickup: WeaponPickup, elapsedMs: number): void {
  if (!pickup.available) {
    setPickupViewVisible(view, false);
    return;
  }

  syncPickupViewVisuals({
    view,
    position: pickup.position,
    motion: resolveWeaponPickupMotionPlan(pickup, elapsedMs),
    style: getWeaponPickupReadabilityStyle(pickup.weaponKind)
  });
}

export function syncItemPickupView(view: PickupView, pickup: ItemPickup, elapsedMs: number): void {
  if (!pickup.available) {
    setPickupViewVisible(view, false);
    return;
  }

  syncPickupViewVisuals({
    view,
    position: pickup.position,
    motion: resolveItemPickupMotionPlan(pickup, elapsedMs),
    style: getItemPickupReadabilityStyle()
  });
}

function createBasePickupView({
  scene,
  position,
  textureKey,
  frameName,
  label,
  style,
  spriteTint
}: CreateBasePickupViewInput): PickupView {
  const plan = resolvePickupViewCreationPlan({
    position,
    textureKey,
    frameName,
    label,
    style,
    spriteTint
  });
  const halo = createPickupCircle(scene, plan.halo);
  const innerRing = createPickupCircle(scene, plan.innerRing);
  const sprite = createPickupSprite(scene, plan.sprite);
  const labelPlate = createPickupRectangle(scene, plan.labelPlate);
  const glint = createPickupRectangle(scene, plan.glint);
  const labelObject = createPickupText(scene, plan.label);

  return { halo, innerRing, sprite, labelPlate, glint, label: labelObject };
}

function syncPickupViewVisuals({
  view,
  position,
  motion,
  style
}: SyncPickupViewVisualsInput): void {
  const plan = resolvePickupViewVisualPlan({
    position,
    motion,
    style,
    labelWidth: view.label.width
  });

  setPickupViewVisible(view, true);
  syncPickupCircle(view.halo, plan.halo);
  syncPickupCircle(view.innerRing, plan.innerRing);
  syncPickupSprite(view.sprite, plan.sprite);
  syncPickupText(view.label, plan.label);
  syncPickupRectangle(view.labelPlate, plan.labelPlate);
  syncPickupRectangle(view.glint, plan.glint);
}

function createPickupCircle(scene: Phaser.Scene, plan: PickupCircleCreationPlan): Phaser.GameObjects.Arc {
  const circle = scene.add
    .circle(plan.position.x, plan.position.y, plan.radius, plan.fillColor, plan.fillAlpha)
    .setDepth(plan.depth);

  if (plan.stroke) {
    circle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return circle;
}

function createPickupRectangle(
  scene: Phaser.Scene,
  plan: PickupRectangleCreationPlan
): Phaser.GameObjects.Rectangle {
  const rectangle = scene.add
    .rectangle(
      plan.position.x,
      plan.position.y,
      plan.size.x,
      plan.size.y,
      plan.fillColor,
      plan.fillAlpha
    )
    .setDepth(plan.depth);

  if (plan.stroke) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return rectangle;
}

function createPickupSprite(scene: Phaser.Scene, plan: PickupSpriteCreationPlan): Phaser.GameObjects.Image {
  const sprite = scene.add
    .image(plan.position.x, plan.position.y, plan.textureKey, plan.frameName)
    .setScale(plan.scale)
    .setDepth(plan.depth);

  if (plan.tint !== undefined) {
    sprite.setTint(plan.tint);
  }

  return sprite;
}

function createPickupText(scene: Phaser.Scene, plan: PickupTextCreationPlan): Phaser.GameObjects.Text {
  return scene.add
    .text(plan.position.x, plan.position.y, plan.label, plan.style)
    .setOrigin(plan.origin.x, plan.origin.y)
    .setDepth(plan.depth);
}

function syncPickupCircle(circle: Phaser.GameObjects.Arc, plan: PickupCircleVisualPlan): void {
  circle.setPosition(plan.position.x, plan.position.y);
  circle.setRadius(plan.radius);

  if (plan.fill) {
    circle.setFillStyle(plan.fill.color, plan.fill.alpha);
  }
  if (plan.stroke) {
    circle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }
}

function syncPickupRectangle(rectangle: Phaser.GameObjects.Rectangle, plan: PickupRectangleVisualPlan): void {
  rectangle.setPosition(plan.position.x, plan.position.y);

  if (plan.size) {
    rectangle.setDisplaySize(plan.size.x, plan.size.y);
  }
  if (plan.rotation !== undefined) {
    rectangle.setRotation(plan.rotation);
  }
  if (plan.fill) {
    rectangle.setFillStyle(plan.fill.color, plan.fill.alpha);
  }
  if (plan.stroke) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }
}

function syncPickupSprite(sprite: Phaser.GameObjects.Image, plan: PickupSpriteVisualPlan): void {
  sprite.setPosition(plan.position.x, plan.position.y);
  sprite.setScale(plan.scale);
}

function syncPickupText(text: Phaser.GameObjects.Text, plan: PickupTextVisualPlan): void {
  text.setPosition(plan.position.x, plan.position.y);
}
