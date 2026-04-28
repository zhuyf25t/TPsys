import Phaser from "phaser";
import type { Vec2, WeaponKind } from "../../../../domain/types";

const HERO_READABILITY_WEAPON_OVERLAY_DEPTH = 40.5;

export interface HeroWeaponOverlayView {
  primary: Phaser.GameObjects.Rectangle;
  secondary: Phaser.GameObjects.Rectangle;
  core: Phaser.GameObjects.Arc;
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

export function createHeroWeaponOverlayView(scene: Phaser.Scene, position: Vec2): HeroWeaponOverlayView {
  return {
    primary: scene.add
      .rectangle(position.x, position.y, 1, 1, 0xffffff, 0)
      .setDepth(HERO_READABILITY_WEAPON_OVERLAY_DEPTH)
      .setVisible(false),
    secondary: scene.add
      .rectangle(position.x, position.y, 1, 1, 0xffffff, 0)
      .setDepth(HERO_READABILITY_WEAPON_OVERLAY_DEPTH)
      .setVisible(false),
    core: scene.add
      .circle(position.x, position.y, 1, 0xffffff, 0)
      .setDepth(HERO_READABILITY_WEAPON_OVERLAY_DEPTH)
      .setVisible(false)
  };
}

export function setHeroWeaponOverlayVisible(view: HeroWeaponOverlayView, visible: boolean): void {
  view.primary.setVisible(visible);
  view.secondary.setVisible(visible);
  view.core.setVisible(visible);
}

export function syncHeroWeaponOverlayVisuals({
  view,
  weaponKind,
  displayPosition,
  displayFacing,
  radius,
  cueOriginOffset,
  cueLength,
  alpha,
  strokeAlpha
}: SyncHeroWeaponOverlayVisualsInput): void {
  const directionX = Math.cos(displayFacing);
  const directionY = Math.sin(displayFacing);
  const perpendicularX = -directionY;
  const perpendicularY = directionX;
  const resolvePosition = (forwardOffset: number, sideOffset: number): Vec2 => ({
    x: displayPosition.x + directionX * forwardOffset + perpendicularX * sideOffset,
    y: displayPosition.y + directionY * forwardOffset + perpendicularY * sideOffset
  });

  setHeroWeaponOverlayVisible(view, false);

  switch (weaponKind) {
    case "Pistol":
      syncWeaponOverlayRectangle(
        view.primary,
        resolvePosition(cueOriginOffset - radius * 0.02, radius * 0.22),
        displayFacing + 1.05,
        radius * 0.32,
        5,
        0x8a593c,
        alpha * 0.72,
        0xfff0c6,
        strokeAlpha * 0.7
      );
      syncWeaponOverlayRectangle(
        view.secondary,
        resolvePosition(cueOriginOffset + cueLength * 0.4, -radius * 0.11),
        displayFacing,
        cueLength * 0.42,
        2.2,
        0xfffbdf,
        alpha * 0.5
      );
      return;
    case "RocketLauncher":
      syncWeaponOverlayRectangle(
        view.primary,
        resolvePosition(cueOriginOffset + cueLength * 0.52, -radius * 0.23),
        displayFacing,
        cueLength * 0.72,
        3,
        0xffd36a,
        alpha * 0.72,
        0xfff1b8,
        strokeAlpha * 0.5
      );
      syncWeaponOverlayRectangle(
        view.secondary,
        resolvePosition(cueOriginOffset + cueLength * 0.22, 0),
        displayFacing,
        4,
        radius * 0.68,
        0x6f331f,
        alpha * 0.68,
        0xffc08a,
        strokeAlpha * 0.8
      );
      syncWeaponOverlayCore(
        view.core,
        resolvePosition(cueOriginOffset + cueLength * 0.96, 0),
        4.5,
        0xff6a2f,
        alpha * 0.7,
        0xffe0a8,
        strokeAlpha
      );
      return;
    case "Gatling":
      syncWeaponOverlayRectangle(
        view.primary,
        resolvePosition(cueOriginOffset + cueLength * 0.54, -radius * 0.14),
        displayFacing,
        cueLength * 0.84,
        2.4,
        0xfff0a3,
        alpha * 0.78
      );
      syncWeaponOverlayRectangle(
        view.secondary,
        resolvePosition(cueOriginOffset + cueLength * 0.54, radius * 0.14),
        displayFacing,
        cueLength * 0.84,
        2.4,
        0xfff0a3,
        alpha * 0.78
      );
      syncWeaponOverlayCore(
        view.core,
        resolvePosition(cueOriginOffset + cueLength * 0.56, 0),
        4.2,
        0xff6f32,
        alpha * 0.62,
        0xfff0a3,
        strokeAlpha
      );
      return;
    case "Shotgun":
      syncWeaponOverlayRectangle(
        view.primary,
        resolvePosition(cueOriginOffset + cueLength * 0.5, -radius * 0.18),
        displayFacing,
        cueLength * 0.86,
        3.4,
        0xfff4cd,
        alpha * 0.74,
        0x6d3b22,
        strokeAlpha * 0.55
      );
      syncWeaponOverlayRectangle(
        view.secondary,
        resolvePosition(cueOriginOffset + cueLength * 0.5, radius * 0.18),
        displayFacing,
        cueLength * 0.86,
        3.4,
        0xfff4cd,
        alpha * 0.74,
        0x6d3b22,
        strokeAlpha * 0.55
      );
      syncWeaponOverlayCore(
        view.core,
        resolvePosition(cueOriginOffset + cueLength * 0.22, 0),
        3.8,
        0xc9773e,
        alpha * 0.64,
        0xffefb7,
        strokeAlpha * 0.9
      );
      return;
  }
}

function syncWeaponOverlayRectangle(
  view: Phaser.GameObjects.Rectangle,
  position: Vec2,
  rotation: number,
  width: number,
  height: number,
  fillTint: number,
  fillAlpha: number,
  strokeTint = fillTint,
  strokeAlpha = 0
): void {
  view.setVisible(true);
  view.setPosition(position.x, position.y);
  view.setRotation(rotation);
  view.setDisplaySize(width, height);
  view.setFillStyle(fillTint, fillAlpha);
  view.setStrokeStyle(strokeAlpha > 0 ? 1 : 0, strokeTint, strokeAlpha);
}

function syncWeaponOverlayCore(
  view: Phaser.GameObjects.Arc,
  position: Vec2,
  radius: number,
  fillTint: number,
  fillAlpha: number,
  strokeTint: number,
  strokeAlpha: number
): void {
  view.setVisible(true);
  view.setPosition(position.x, position.y);
  view.setRadius(radius);
  view.setFillStyle(fillTint, fillAlpha);
  view.setStrokeStyle(strokeAlpha > 0 ? 1 : 0, strokeTint, strokeAlpha);
}
