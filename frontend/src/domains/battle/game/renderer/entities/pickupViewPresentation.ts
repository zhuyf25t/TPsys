import Phaser from "phaser";
import type { ItemPickup, Vec2, WeaponKind, WeaponPickup } from "../../../objects/types";
import { CRATE_TEXTURE_KEY, WEAPON_PICKUP_ICON_KEYS } from "../../constants";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel, getWeaponPickupTint } from "../../../components/presenters/battleDisplayCatalog";

const PICKUP_HALO_DEPTH = 61;
const PICKUP_INNER_RING_DEPTH = 61.5;
const PICKUP_LABEL_PLATE_DEPTH = 62.5;
const PICKUP_SPRITE_DEPTH = 62;
const PICKUP_GLINT_DEPTH = 62.75;
const PICKUP_LABEL_DEPTH = 63;

interface PickupReadabilityStyle {
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

const WEAPON_PICKUP_READABILITY_STYLES: Record<WeaponKind, PickupReadabilityStyle> = {
  Pistol: {
    radius: 32,
    fillTint: 0x20394b,
    fillAlpha: 0.22,
    strokeTint: 0xaeeeff,
    strokeAlpha: 0.58,
    strokeWidth: 1,
    spriteScale: 0.88,
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
    spriteScale: 1.08,
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
    spriteScale: 0.98,
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
    spriteScale: 1,
    labelColor: "#fff0ce",
    labelPlateTint: 0x2f1c10,
    labelPlateAlpha: 0.78,
    glintTint: 0xfff4cd
  }
};

const ITEM_PICKUP_READABILITY_STYLE: PickupReadabilityStyle = {
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

export function createWeaponPickupView(scene: Phaser.Scene, pickup: WeaponPickup): PickupView {
  const style = getWeaponPickupReadabilityStyle(pickup.weaponKind);
  const view = createBasePickupView({
    scene,
    position: pickup.position,
    textureKey: WEAPON_PICKUP_ICON_KEYS[pickup.weaponKind],
    label: getWeaponDisplayLabel(pickup.weaponKind),
    style
  });
  view.sprite.setTint(getWeaponPickupTint(pickup.weaponKind));
  return view;
}

export function createItemPickupView(scene: Phaser.Scene, pickup: ItemPickup): PickupView {
  const view = createBasePickupView({
    scene,
    position: pickup.position,
    textureKey: CRATE_TEXTURE_KEY,
    label: getItemPickupDisplayLabel(pickup.kind),
    style: ITEM_PICKUP_READABILITY_STYLE
  });
  view.sprite.setTint(ITEM_PICKUP_READABILITY_STYLE.strokeTint);
  return view;
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

  const style = getWeaponPickupReadabilityStyle(pickup.weaponKind);
  const bob = Math.sin((elapsedMs + pickup.position.x) / 240) * 4;
  const pulse = 0.5 + Math.sin((elapsedMs + pickup.position.y) / 360) * 0.5;
  syncPickupViewVisuals({
    view,
    position: pickup.position,
    bob,
    pulse,
    style,
    strokePulseAlpha: pulse * 0.1,
    glintRotation: -0.42 + pulse * 0.14
  });
}

export function syncItemPickupView(view: PickupView, pickup: ItemPickup, elapsedMs: number): void {
  if (!pickup.available) {
    setPickupViewVisible(view, false);
    return;
  }

  const bob = Math.sin((elapsedMs + pickup.position.x) / 260) * 3;
  const pulse = 0.5 + Math.sin((elapsedMs + pickup.position.y) / 420) * 0.5;
  syncPickupViewVisuals({
    view,
    position: pickup.position,
    bob,
    pulse,
    style: ITEM_PICKUP_READABILITY_STYLE,
    strokePulseAlpha: pulse * 0.08,
    glintRotation: 0.48 - pulse * 0.12
  });
}

interface CreateBasePickupViewInput {
  scene: Phaser.Scene;
  position: Vec2;
  textureKey: string;
  label: string;
  style: PickupReadabilityStyle;
}

function createBasePickupView({
  scene,
  position,
  textureKey,
  label,
  style
}: CreateBasePickupViewInput): PickupView {
  const halo = scene.add.circle(position.x, position.y, style.radius, style.fillTint, style.fillAlpha).setDepth(PICKUP_HALO_DEPTH);
  halo.setStrokeStyle(style.strokeWidth, style.strokeTint, style.strokeAlpha);

  const innerRing = scene.add.circle(position.x, position.y, style.radius * 0.58, style.strokeTint, 0).setDepth(PICKUP_INNER_RING_DEPTH);
  innerRing.setStrokeStyle(1, style.strokeTint, 0.32);

  const sprite = scene.add.image(position.x, position.y, textureKey).setScale(style.spriteScale).setDepth(PICKUP_SPRITE_DEPTH);

  const labelPlate = scene.add
    .rectangle(position.x, position.y + 34, 72, 20, style.labelPlateTint, style.labelPlateAlpha)
    .setDepth(PICKUP_LABEL_PLATE_DEPTH);
  labelPlate.setStrokeStyle(1, style.strokeTint, 0.24);

  const glint = scene.add
    .rectangle(position.x + style.radius * 0.3, position.y - style.radius * 0.32, 14, 2, style.glintTint, 0.74)
    .setDepth(PICKUP_GLINT_DEPTH);

  const labelObject = scene.add
    .text(position.x, position.y + 26, label, {
      fontFamily: "Segoe UI",
      fontSize: "12px",
      color: style.labelColor
    })
    .setOrigin(0.5, 0)
    .setDepth(PICKUP_LABEL_DEPTH);

  return { halo, innerRing, sprite, labelPlate, glint, label: labelObject };
}

interface SyncPickupViewVisualsInput {
  view: PickupView;
  position: Vec2;
  bob: number;
  pulse: number;
  style: PickupReadabilityStyle;
  strokePulseAlpha: number;
  glintRotation: number;
}

function syncPickupViewVisuals({
  view,
  position,
  bob,
  pulse,
  style,
  strokePulseAlpha,
  glintRotation
}: SyncPickupViewVisualsInput): void {
  setPickupViewVisible(view, true);

  view.halo.setPosition(position.x, position.y);
  view.halo.setRadius(style.radius + pulse * 2);
  view.halo.setFillStyle(style.fillTint, style.fillAlpha);
  view.halo.setStrokeStyle(style.strokeWidth, style.strokeTint, style.strokeAlpha + strokePulseAlpha);

  view.innerRing.setPosition(position.x, position.y);
  view.innerRing.setRadius(style.radius * 0.58 + pulse);
  view.innerRing.setFillStyle(style.strokeTint, 0);
  view.innerRing.setStrokeStyle(1, style.strokeTint, 0.26 + pulse * 0.12);

  view.sprite.setPosition(position.x, position.y + bob);
  view.sprite.setScale(style.spriteScale);

  view.label.setPosition(position.x, position.y + 26);
  view.labelPlate.setPosition(position.x, position.y + 34);
  view.labelPlate.setDisplaySize(Math.max(72, view.label.width + 18), 20);
  view.labelPlate.setFillStyle(style.labelPlateTint, style.labelPlateAlpha);
  view.labelPlate.setStrokeStyle(1, style.strokeTint, 0.22 + pulse * 0.08);

  view.glint.setPosition(position.x + style.radius * 0.3, position.y - style.radius * 0.32 + bob * 0.35);
  view.glint.setRotation(glintRotation);
  view.glint.setDisplaySize(14 + pulse * 4, 2);
  view.glint.setFillStyle(style.glintTint, 0.55 + pulse * 0.22);
}

function getWeaponPickupReadabilityStyle(weaponKind: WeaponKind): PickupReadabilityStyle {
  return WEAPON_PICKUP_READABILITY_STYLES[weaponKind];
}
