import Phaser from "phaser";
import type { Vec2 } from "../../../objects/types";
import {
  CRATE_TEXTURE_KEY,
  ROCK_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WALL_TEXTURE_KEY
} from "../../constants";
import { ITEM_PICKUP_SPAWN_POINTS, WEAPON_PICKUP_SPAWN_POINTS } from "../../spawn";
import type { OccludableSprite, OccludableView } from "./arenaBuilder";

const ARENA_ENERGY_ACCENT_COLOR = 0x58d6ff;

/** 中文名：创建拾取物pads（createPickupPads）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createPickupPads(scene: Phaser.Scene): void {
  WEAPON_PICKUP_SPAWN_POINTS.forEach((point) => {
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

  ITEM_PICKUP_SPAWN_POINTS.forEach((point) => {
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
    baseAlpha
  });
}
