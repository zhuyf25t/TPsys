import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import {
  resolveHeroReadabilityCreationPlan,
  resolveHeroHealthVisualPlan,
  resolveHeroReadabilityVisualPlan
} from "./functions/HeroReadabilityViewRules";
import { setHeroWeaponOverlayVisible, syncHeroWeaponOverlayVisuals } from "./heroWeaponOverlayView";
import {
  type HeroReadabilityCircleCreationPlan,
  type HeroReadabilityLegacyWeaponCueVisibilityPlan,
  type HeroReadabilityRectangleCreationPlan,
  type HeroHealthView,
  type HeroReadabilitySyncView,
  type HeroReadabilityView
} from "./objects/HeroReadabilityViewObjects";

export type {
  HeroHealthView,
  HeroReadabilitySyncView,
  HeroReadabilityView,
  WeaponCueReadabilityStyle
} from "./objects/HeroReadabilityViewObjects";


export function createHeroReadabilityView(scene: Phaser.Scene, hero: Hero, isPlayer: boolean): HeroReadabilityView {
  const creationPlan = resolveHeroReadabilityCreationPlan({ hero, isPlayer });
  const shadow = createHeroReadabilityCircle(scene, creationPlan.shadow);
  const bodyDisc = createHeroReadabilityCircle(scene, creationPlan.bodyDisc);
  const silhouetteRing = createHeroReadabilityCircle(scene, creationPlan.silhouetteRing);
  const hitRing = createHeroReadabilityCircle(scene, creationPlan.hitRing);
  const statusRing = createHeroReadabilityCircle(scene, creationPlan.statusRing);
  const weaponStock = createHeroReadabilityRectangle(scene, creationPlan.weaponStock);
  const weaponCue = createHeroReadabilityRectangle(scene, creationPlan.weaponCue);
  const weaponMuzzle = createHeroReadabilityCircle(scene, creationPlan.weaponMuzzle);
  const marker = creationPlan.marker ? createHeroReadabilityCircle(scene, creationPlan.marker) : null;

  return {
    shadow,
    bodyDisc,
    silhouetteRing,
    hitRing,
    statusRing,
    weaponStock,
    weaponCue,
    weaponMuzzle,
    marker
  };
}

function createHeroReadabilityCircle(
  scene: Phaser.Scene,
  plan: HeroReadabilityCircleCreationPlan
): Phaser.GameObjects.Arc {
  const circle = scene.add
    .circle(plan.position.x, plan.position.y, plan.radius, plan.fillColor, plan.fillAlpha)
    .setDepth(plan.depth)
    .setVisible(plan.visible);

  if (plan.stroke) {
    circle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return circle;
}

function createHeroReadabilityRectangle(
  scene: Phaser.Scene,
  plan: HeroReadabilityRectangleCreationPlan
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
    .setDepth(plan.depth)
    .setVisible(plan.visible);

  if (plan.origin) {
    rectangle.setOrigin(plan.origin.x, plan.origin.y);
  }
  if (plan.stroke) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return rectangle;
}


export function syncHeroReadabilityVisuals(
  view: HeroReadabilitySyncView,
  hero: Hero,
  displayPosition: Vec2,
  displayFacing: number,
  isPlayer: boolean,
  slowFields: readonly SlowField[]
): void {
  const plan = resolveHeroReadabilityVisualPlan({
    hero,
    displayPosition,
    displayFacing,
    isPlayer,
    slowFields
  });

  view.shadow.setPosition(plan.shadow.position.x, plan.shadow.position.y);
  view.shadow.setRadius(plan.shadow.radius);
  view.bodyDisc.setPosition(plan.bodyDisc.position.x, plan.bodyDisc.position.y);
  view.bodyDisc.setRadius(plan.bodyDisc.radius);
  view.silhouetteRing.setPosition(plan.silhouetteRing.position.x, plan.silhouetteRing.position.y);
  view.silhouetteRing.setRadius(plan.silhouetteRing.radius);
  view.hitRing.setPosition(plan.hitRing.position.x, plan.hitRing.position.y);
  view.hitRing.setRadius(plan.hitRing.radius);
  view.statusRing.setVisible(plan.statusRing.visible);
  if (plan.statusRing.visible) {
    view.statusRing.setPosition(plan.statusRing.position.x, plan.statusRing.position.y);
    view.statusRing.setRadius(plan.statusRing.radius);
    view.statusRing.setFillStyle(plan.statusRing.fillColor, plan.statusRing.fillAlpha);
    view.statusRing.setStrokeStyle(plan.statusRing.strokeWidth, plan.statusRing.strokeColor, plan.statusRing.strokeAlpha);
  }

  if (isPlainZombieNpcHero(hero)) {
    hideHeroReadabilityWeaponCues(view);
    setHeroWeaponOverlayVisible(view.weaponOverlay, false);
  } else {
    applyHeroReadabilityLegacyWeaponCueVisibilityPlan(view, plan.legacyWeaponCue);
    syncHeroWeaponOverlayVisuals({
      view: view.weaponOverlay,
      ...plan.weaponOverlay
    });
  }
}

function isPlainZombieNpcHero(hero: Hero): boolean {
  return isZombieNpcHero(hero) && !isBossZombieHero(hero);
}

function isBossZombieHero(hero: Hero): boolean {
  return hero.heroId === "bot-1" || hero.heroId === "bot-2" || hero.heroId === "bot-3";
}

function isZombieNpcHero(hero: Hero): boolean {
  return hero.heroId.startsWith("bot-") || hero.displayName.toLowerCase().includes("zombie");
}

function hideHeroReadabilityWeaponCues(view: HeroReadabilitySyncView): void {
  applyVisible(view.weaponStock, false);
  applyVisible(view.weaponCue, false);
  applyVisible(view.weaponMuzzle, false);
}

function applyHeroReadabilityLegacyWeaponCueVisibilityPlan(
  view: HeroReadabilitySyncView,
  plan: HeroReadabilityLegacyWeaponCueVisibilityPlan
): void {
  applyVisible(view.weaponStock, plan.stockVisible);
  applyVisible(view.weaponCue, plan.cueVisible);
  applyVisible(view.weaponMuzzle, plan.muzzleVisible);
}

function applyVisible(target: { visible: boolean; setVisible: (visible: boolean) => unknown }, visible: boolean): void {
  if (target.visible !== visible) {
    target.setVisible(visible);
  }
}


export function syncHeroHealthVisuals(view: HeroHealthView, hero: Hero, elapsedMs: number): void {
  const plan = resolveHeroHealthVisualPlan(hero, elapsedMs);

  view.healthFill.displayWidth = plan.fillWidth;
  view.healthFill.setFillStyle(plan.fillTint, plan.fillAlpha);
  view.healthBackground.setFillStyle(plan.backgroundTint, plan.backgroundAlpha);
}
