import Phaser from "phaser";
import type { Vec2 } from "../../../../../objects/battle/types";
import {
  CRATE_TEXTURE_KEY,
  getActiveBattleMap,
  isNaturalBattleMapTheme,
  ROCK_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WALL_TEXTURE_KEY,
  type BattleMapThemeId
} from "../../constants";
import { getItemPickupSpawnPoints, getWeaponPickupSpawnPoints } from "../../assets/battleContentCatalog";
import type { OccludableSprite, OccludableView } from "./arenaBuilder";

const ARENA_ENERGY_ACCENT_COLOR = 0x58d6ff;

/** 中文名：创建拾取物pads（createPickupPads）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createPickupPads(scene: Phaser.Scene): void {
  if (isNaturalBattleMapTheme(getActiveBattleMap().themeId)) {
    createNaturalPickupPads(scene, getActiveBattleMap().themeId);
    return;
  }

  getWeaponPickupSpawnPoints().forEach((point) => {
    scene.add.tileSprite(point.position.x, point.position.y + 8, 112, 82, STONE_TRIM_TEXTURE_KEY).setDepth(-12).setTint(0x29343a).setAlpha(0.98);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 118, 88, 0x11181c, 0.18)
      .setStrokeStyle(2, 0xd99a34, 0.72)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 86, 50, 0xf0bd58, 0.06)
      .setStrokeStyle(1, 0xf0bd58, 0.46)
      .setDepth(-10);
  });

  getItemPickupSpawnPoints().forEach((point) => {
    scene.add
      .tileSprite(point.position.x, point.position.y + 8, 100, 76, STONE_TRIM_TEXTURE_KEY)
      .setDepth(-12)
      .setTint(0x21343c)
      .setAlpha(0.96);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 106, 82, 0x0d1a1e, 0.18)
      .setStrokeStyle(2, ARENA_ENERGY_ACCENT_COLOR, 0.72)
      .setDepth(-11);
    scene.add
      .rectangle(point.position.x, point.position.y + 8, 72, 44, 0x8ff3ff, 0.06)
      .setStrokeStyle(1, 0x8ff3ff, 0.4)
      .setDepth(-10);
  });
}

interface NaturalPickupPadPalette {
  weaponShadow: number;
  weaponOuter: number;
  weaponInner: number;
  itemShadow: number;
  itemOuter: number;
  itemInner: number;
}

function createNaturalPickupPads(scene: Phaser.Scene, themeId: BattleMapThemeId): void {
  const palette = naturalPickupPadPalette(themeId);

  getWeaponPickupSpawnPoints().forEach((point) => {
    scene.add.ellipse(point.position.x + 5, point.position.y + 9, 104, 68, palette.weaponShadow, 0.18).setDepth(19);
    scene.add.ellipse(point.position.x, point.position.y + 4, 98, 58, palette.weaponOuter, 0.2).setDepth(20);
    scene.add.ellipse(point.position.x, point.position.y + 4, 68, 34, palette.weaponInner, 0.12).setDepth(21);
  });

  getItemPickupSpawnPoints().forEach((point) => {
    scene.add.ellipse(point.position.x + 5, point.position.y + 9, 92, 62, palette.itemShadow, 0.18).setDepth(19);
    scene.add.ellipse(point.position.x, point.position.y + 4, 84, 50, palette.itemOuter, 0.22).setDepth(20);
    scene.add.ellipse(point.position.x, point.position.y + 4, 58, 30, palette.itemInner, 0.12).setDepth(21);
  });
}

function naturalPickupPadPalette(themeId: BattleMapThemeId): NaturalPickupPadPalette {
  switch (themeId) {
    case "winter":
      return {
        weaponShadow: 0x8aa7b4,
        weaponOuter: 0xdceef5,
        weaponInner: 0xffffff,
        itemShadow: 0x7ea5b4,
        itemOuter: 0xb9dbe8,
        itemInner: 0xedfbff
      };
    case "normal":
      return {
        weaponShadow: 0x243018,
        weaponOuter: 0x7d8b3c,
        weaponInner: 0xd4b85a,
        itemShadow: 0x1f301d,
        itemOuter: 0x4f7b42,
        itemInner: 0x9fdd7a
      };
    case "fall":
      return {
        weaponShadow: 0x2b2f1d,
        weaponOuter: 0xa57634,
        weaponInner: 0xe0b15e,
        itemShadow: 0x20301f,
        itemOuter: 0x527546,
        itemInner: 0x9be77d
      };
    case "industrial":
      return {
        weaponShadow: 0x2b2f1d,
        weaponOuter: 0xa57634,
        weaponInner: 0xe0b15e,
        itemShadow: 0x20301f,
        itemOuter: 0x527546,
        itemInner: 0x9be77d
      };
  }
}

/** 中文名：创建竞技场decorations（createArenaDecorations）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createArenaDecorations(scene: Phaser.Scene, occludables: OccludableView[]): void {
  const pylons: readonly Vec2[] = [
    { x: 320, y: 320 },
    { x: 2240, y: 320 },
    { x: 320, y: 1280 },
    { x: 2240, y: 1280 },
    { x: 736, y: 224 },
    { x: 1824, y: 224 },
    { x: 736, y: 1376 },
    { x: 1824, y: 1376 }
  ];

  const machinery: readonly Vec2[] = [
    { x: 896, y: 448 },
    { x: 1664, y: 448 },
    { x: 896, y: 1152 },
    { x: 1664, y: 1152 },
    { x: 640, y: 640 },
    { x: 1920, y: 640 },
    { x: 640, y: 960 },
    { x: 1920, y: 960 }
  ];

  const lowDeckPlates: readonly Vec2[] = [
    { x: 544, y: 576 },
    { x: 2016, y: 576 },
    { x: 544, y: 1024 },
    { x: 2016, y: 1024 },
    { x: 1280, y: 224 },
    { x: 1280, y: 1376 }
  ];

  pylons.forEach((position) => {
    scene.add.rectangle(position.x + 8, position.y + 16, 82, 96, 0x020405, 0.36).setDepth(43);
    const pylon = scene.add.image(position.x, position.y, WALL_TEXTURE_KEY).setScale(1.16).setDepth(54).setTint(0x1b252c).setAlpha(0.96);
    scene.add.rectangle(position.x, position.y + 30, 78, 7, ARENA_ENERGY_ACCENT_COLOR, 0.2).setDepth(55);
    registerDecorativeOccludable(pylon, 0.96, occludables);
  });

  machinery.forEach((position) => {
    scene.add.rectangle(position.x + 10, position.y + 12, 74, 58, 0x020405, 0.32).setDepth(42);
    const machine = scene.add.image(position.x, position.y, ROCK_TEXTURE_KEY).setScale(1.08).setDepth(53).setTint(0x2b363b).setAlpha(0.92);
    scene.add.rectangle(position.x, position.y - 24, 44, 4, 0xd99a34, 0.22).setDepth(54);
    registerDecorativeOccludable(machine, 0.92, occludables);
  });

  lowDeckPlates.forEach((position) => {
    scene.add.image(position.x, position.y, CRATE_TEXTURE_KEY).setScale(1.1).setDepth(11).setTint(0x1f2b31).setAlpha(0.78);
    scene.add.rectangle(position.x, position.y, 74, 6, 0x5fd9ff, 0.14).setDepth(12);
  });
}

function registerDecorativeOccludable(sprite: OccludableSprite, baseAlpha: number, occludables: OccludableView[]): void {
  const bounds = sprite.getBounds();
  occludables.push({
    sprite,
    bounds: new Phaser.Geom.Rectangle(bounds.x, bounds.y, bounds.width, bounds.height),
    baseAlpha,
    mode: "local-probe"
  });
}
