import {
  createHeroWeaponOverlayView
} from "./heroWeaponOverlayView";
import type Phaser from "phaser";
import {
  createHeroReadabilityView
} from "./heroReadabilityView";
import {
  createLocalHeroMotionStreakView
} from "./localHeroMotionStreakView";
import { resolveHeroWorldViewCreationPlan } from "./functions/WorldViewFactoryRules";
import type {
  CreateHeroWorldViewInput,
  HeroWorldViewRectangleCreationPlan,
  HeroWorldViewStrokeRectangleCreationPlan
} from "./objects/HeroWorldViewFactoryObjects";
import type { HeroView } from "./objects/WorldViewFactoryObjects";

export function createHeroWorldView({
  scene,
  hero,
  playerHeroId,
  getBaseHeroScale
}: CreateHeroWorldViewInput): HeroView {
  const creationPlan = resolveHeroWorldViewCreationPlan({
    hero,
    playerHeroId,
    baseHeroScale: getBaseHeroScale(hero.heroId)
  });
  const readabilityView = createHeroReadabilityView(scene, hero, creationPlan.isPlayer);
  const weaponOverlay = createHeroWeaponOverlayView(scene, hero.position);
  const sprite = scene.add
    .image(hero.position.x, hero.position.y, creationPlan.textureKey)
    .setScale(creationPlan.baseScale)
    .setTint(creationPlan.tint)
    .setDepth(creationPlan.spriteDepth);

  const localMotionStreaks = creationPlan.isPlayer ? createLocalHeroMotionStreakView(scene, hero.position) : null;

  const nameLabelPlan = creationPlan.nameLabel;
  const nameLabel = scene.add
    .text(nameLabelPlan.position.x, nameLabelPlan.position.y, nameLabelPlan.text, nameLabelPlan.style)
    .setOrigin(nameLabelPlan.origin.x, nameLabelPlan.origin.y)
    .setDepth(nameLabelPlan.depth);

  const healthBackground = createHeroWorldViewRectangle(scene, creationPlan.healthBackground);
  const healthFill = createHeroWorldViewRectangle(scene, creationPlan.healthFill);
  const actionBackground = createHeroWorldViewStrokeRectangle(scene, creationPlan.actionBackground);
  const actionFill = createHeroWorldViewRectangle(scene, creationPlan.actionFill);

  return {
    localMotionStreaks,
    ...readabilityView,
    weaponOverlay,
    sprite,
    nameLabel,
    healthBackground,
    healthFill,
    actionBackground,
    actionFill
  };
}

function createHeroWorldViewRectangle(
  scene: CreateHeroWorldViewInput["scene"],
  plan: HeroWorldViewRectangleCreationPlan
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

  return rectangle;
}

function createHeroWorldViewStrokeRectangle(
  scene: CreateHeroWorldViewInput["scene"],
  plan: HeroWorldViewStrokeRectangleCreationPlan
): Phaser.GameObjects.Rectangle {
  const rectangle = createHeroWorldViewRectangle(scene, plan);
  if (plan.stroke) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return rectangle;
}
