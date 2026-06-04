import Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  CRATE_TEXTURE_KEY,
  getActiveBattleMap,
  isNaturalBattleMapTheme,
  ROCK_TEXTURE_KEY,
  STONE_TRIM_TEXTURE_KEY,
  WALL_TEXTURE_KEY
} from "../../objects/BattleGameConstants";
import { getItemPickupSpawnPoints, getWeaponPickupSpawnPoints } from "../../../microservices/world/functions/BattleWorldInitialLayout";
import type { OccludableSprite, OccludableView } from "./objects/ArenaBuilderObjects";
import type {
  ArenaDecorationElementPlan,
  ArenaDecorationEllipsePlan,
  ArenaDecorationImagePlan,
  ArenaDecorationPatternPlan,
  ArenaDecorationPresentationPlan,
  ArenaDecorationRectanglePlan,
  ArenaDecorationTextureRole,
  ArenaPickupPadPresentationPlan
} from "./objects/ArenaDecorationObjects";
import {
  resolveIndustrialArenaDecorationPresentationPlan,
  resolveIndustrialPickupPadPresentationPlan,
  resolveNaturalPickupPadPresentationPlan
} from "./functions/ArenaDecorationRules";

/** 涓枃鍚嶏細鍒涘缓鎷惧彇鐗﹑ads锛坈reatePickupPads锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷淬€?*/
export function createPickupPads(scene: Phaser.Scene): void {
  const activeMap = getActiveBattleMap();
  const weaponPickupSpawnPoints = getWeaponPickupSpawnPoints();
  const itemPickupSpawnPoints = getItemPickupSpawnPoints();
  const plan = isNaturalBattleMapTheme(activeMap.themeId)
    ? resolveNaturalPickupPadPresentationPlan(activeMap.themeId, weaponPickupSpawnPoints, itemPickupSpawnPoints)
    : resolveIndustrialPickupPadPresentationPlan(weaponPickupSpawnPoints, itemPickupSpawnPoints);

  renderPickupPadPresentationPlan(scene, plan);
}

function renderPickupPadPresentationPlan(scene: Phaser.Scene, plan: ArenaPickupPadPresentationPlan): void {
  plan.patterns.forEach((pattern) => {
    createDecorationPattern(scene, pattern);
  });

  plan.rectangles.forEach((rectangle) => {
    createDecorationRectangle(scene, rectangle);
  });

  plan.ellipses.forEach((ellipse) => {
    createDecorationEllipse(scene, ellipse);
  });
}

function createDecorationPattern(scene: Phaser.Scene, plan: ArenaDecorationPatternPlan): void {
  const sprite = scene.add
    .tileSprite(plan.position.x, plan.position.y, plan.size.x, plan.size.y, textureKeyForDecorationRole(plan.textureRole))
    .setDepth(plan.depth)
    .setAlpha(plan.alpha);

  if (plan.tint !== undefined) {
    sprite.setTint(plan.tint);
  }
}

function createDecorationRectangle(scene: Phaser.Scene, plan: ArenaDecorationRectanglePlan): void {
  const rectangle = scene.add
    .rectangle(plan.position.x, plan.position.y, plan.size.x, plan.size.y, plan.color, plan.alpha)
    .setDepth(plan.depth);

  if (plan.stroke !== undefined) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }
}

function createDecorationEllipse(scene: Phaser.Scene, plan: ArenaDecorationEllipsePlan): void {
  scene.add.ellipse(plan.position.x, plan.position.y, plan.size.x, plan.size.y, plan.color, plan.alpha).setDepth(plan.depth);
}

function createDecorationImage(scene: Phaser.Scene, plan: ArenaDecorationImagePlan, occludables: OccludableView[]): void {
  const image = scene.add
    .image(plan.position.x, plan.position.y, textureKeyForDecorationRole(plan.textureRole))
    .setScale(plan.scale)
    .setDepth(plan.depth);

  if (plan.tint !== undefined) {
    image.setTint(plan.tint);
  }

  image.setAlpha(plan.alpha);

  if (plan.occludableBaseAlpha !== undefined) {
    registerDecorativeOccludable(image, plan.occludableBaseAlpha, occludables);
  }
}

function textureKeyForDecorationRole(textureRole: ArenaDecorationTextureRole): string {
  switch (textureRole) {
    case "crate":
      return CRATE_TEXTURE_KEY;
    case "rock":
      return ROCK_TEXTURE_KEY;
    case "stoneTrim":
      return STONE_TRIM_TEXTURE_KEY;
    case "wall":
      return WALL_TEXTURE_KEY;
  }
}
/** 涓枃鍚嶏細鍒涘缓绔炴妧鍦篸ecorations锛坈reateArenaDecorations锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷淬€?*/
export function createArenaDecorations(scene: Phaser.Scene, occludables: OccludableView[]): void {
  renderArenaDecorationPresentationPlan(scene, resolveIndustrialArenaDecorationPresentationPlan(), occludables);
}

function renderArenaDecorationPresentationPlan(
  scene: Phaser.Scene,
  plan: ArenaDecorationPresentationPlan,
  occludables: OccludableView[]
): void {
  plan.elements.forEach((element) => {
    createArenaDecorationElement(scene, element, occludables);
  });
}

function createArenaDecorationElement(
  scene: Phaser.Scene,
  element: ArenaDecorationElementPlan,
  occludables: OccludableView[]
): void {
  switch (element.kind) {
    case "rectangle":
      createDecorationRectangle(scene, element.rectangle);
      return;
    case "image":
      createDecorationImage(scene, element.image, occludables);
      return;
  }
}

export function createWinterZombieSetPieces(scene: Phaser.Scene, occludables: OccludableView[]): void {
  if (getActiveBattleMap().themeId !== "winter") {
    return;
  }

  const worldSize = getActiveBattleMap().worldSize;
  createCrashedAircraftSite(scene, { x: worldSize.x * 0.76, y: worldSize.y * 0.2 }, occludables);
  createMedicalLabSite(scene, { x: worldSize.x * 0.19, y: worldSize.y * 0.75 }, occludables);
}

function createCrashedAircraftSite(scene: Phaser.Scene, position: Vec2, occludables: OccludableView[]): void {
  scene.add.image(position.x - 18, position.y + 18, "suroi-zombie-explosion-decal").setDisplaySize(220, 140).setDepth(25).setAlpha(0.72);
  scene.add.ellipse(position.x + 16, position.y + 28, 230, 96, 0x09110c, 0.32).setRotation(-0.42).setDepth(26);

  const aircraft = scene.add
    .image(position.x, position.y, "suroi-zombie-airdrop-plane")
    .setDisplaySize(205, 92)
    .setRotation(-0.52)
    .setDepth(61)
    .setTint(0x3b4649)
    .setAlpha(0.94);
  registerDecorativeOccludable(aircraft, 0.94, occludables);

  scene.add.image(position.x - 92, position.y + 52, "suroi-zombie-used-flare").setDisplaySize(52, 22).setRotation(-0.3).setDepth(34);
  createPulsingLight(scene, position.x - 92, position.y + 52, 0xff4f45, 34, 0.2, 0.88, 760);
  createSmokeColumn(scene, position.x + 54, position.y - 18, 1.1);
  createSmokeColumn(scene, position.x - 20, position.y + 20, 0.8);
}

function createMedicalLabSite(scene: Phaser.Scene, position: Vec2, occludables: OccludableView[]): void {
  scene.add.ellipse(position.x + 8, position.y + 16, 314, 210, 0x0a1616, 0.3).setDepth(17);
  scene.add.image(position.x, position.y, "suroi-zombie-lab-floor").setDisplaySize(292, 184).setDepth(24).setAlpha(0.98);
  scene.add.image(position.x - 62, position.y + 52, "suroi-zombie-blood-decal").setDisplaySize(76, 54).setRotation(0.4).setDepth(35).setAlpha(0.82);
  scene.add.image(position.x + 58, position.y + 26, "suroi-zombie-large-medical-bed").setDisplaySize(88, 40).setRotation(-0.08).setDepth(42);
  scene.add.image(position.x - 72, position.y - 24, "suroi-zombie-small-medical-bed").setDisplaySize(66, 34).setRotation(0.2).setDepth(42);
  scene.add.image(position.x + 104, position.y - 58, "suroi-zombie-hazel-crate").setDisplaySize(50, 50).setDepth(43);

  const tankPositions: readonly Vec2[] = [
    { x: position.x - 12, y: position.y - 56 },
    { x: position.x + 36, y: position.y - 54 },
    { x: position.x + 82, y: position.y - 18 }
  ];
  tankPositions.forEach((tank, index) => {
    scene.add.rectangle(tank.x, tank.y, 18, 46, 0x9cff6f, 0.18).setDepth(44).setStrokeStyle(2, 0xb7ff7a, 0.5);
    createPulsingLight(scene, tank.x, tank.y, 0x9cff6f, 45, 0.16, 0.48, 950 + index * 120);
  });

  const ceiling = scene.add
    .image(position.x, position.y - 6, "suroi-zombie-lab-ceiling")
    .setDisplaySize(296, 188)
    .setDepth(73)
    .setAlpha(0.92)
    .setTint(0xdaf5f2);
  registerDecorativeOccludable(ceiling, 0.92, occludables);

  createPulsingLight(scene, position.x - 128, position.y - 82, 0xff4f45, 75, 0.2, 0.82, 620);
  createPulsingLight(scene, position.x + 122, position.y + 80, 0x7dff56, 75, 0.14, 0.58, 880);
}

function createPulsingLight(
  scene: Phaser.Scene,
  x: number,
  y: number,
  color: number,
  depth: number,
  minAlpha: number,
  maxAlpha: number,
  durationMs: number
): void {
  const light = scene.add.circle(x, y, 22, color, minAlpha).setDepth(depth).setBlendMode(Phaser.BlendModes.ADD);
  scene.tweens.add({
    targets: light,
    alpha: maxAlpha,
    scale: 1.45,
    duration: durationMs,
    yoyo: true,
    repeat: -1,
    ease: "Sine.easeInOut"
  });
}

function createSmokeColumn(scene: Phaser.Scene, x: number, y: number, scale: number): void {
  for (let index = 0; index < 5; index += 1) {
    const puff = scene.add
      .ellipse(x + index * 9, y - index * 18, 42 * scale + index * 9, 26 * scale + index * 7, 0x1f2a2b, 0.16)
      .setDepth(74 + index)
      .setBlendMode(Phaser.BlendModes.MULTIPLY);
    scene.tweens.add({
      targets: puff,
      x: puff.x + 20,
      y: puff.y - 22,
      alpha: 0.04,
      scale: 1.35,
      duration: 2200 + index * 260,
      yoyo: true,
      repeat: -1,
      ease: "Sine.easeInOut"
    });
  }
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
