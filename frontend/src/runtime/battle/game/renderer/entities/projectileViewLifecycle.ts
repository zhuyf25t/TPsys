import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type Phaser from "phaser";
import {
  resolveProjectileViewAcquirePlan,
  resolveProjectileViewCreationPlan,
  resolveProjectileViewReleasePlan,
  resolveProjectileViewTexturePlan,
  resolveProjectileViewVisualPlan
} from "./functions/ProjectilePresentationRules";
import type {
  ProjectileViewActivationPlan,
  ProjectileViewCreationPlan,
  ProjectileViewState,
  ProjectileView,
  ProjectileViewTexturePlan,
  ProjectileViewVisualPlan
} from "./objects/ProjectileViewObjects";

export function acquireProjectileView(
  scene: Phaser.Scene,
  worldViews: ProjectileViewState,
  projectile: Projectile
): ProjectileView {
  const reused = worldViews.projectileViewPool.pop();
  if (!reused) {
    return createProjectileView(scene, projectile);
  }

  applyProjectileViewActivationPlan(reused, resolveProjectileViewAcquirePlan());
  configureProjectileView(reused, projectile);
  syncProjectileViewVisuals(reused, projectile, projectile.position, projectile.facing);
  return reused;
}

export function releaseProjectileView(worldViews: ProjectileViewState, view: ProjectileView): void {
  const releasePlan = resolveProjectileViewReleasePlan(worldViews.projectileViewPool.length);
  applyProjectileViewActivationPlan(view, releasePlan);

  if (releasePlan.destroy) {
    destroyProjectileView(view);
    return;
  }

  worldViews.projectileViewPool.push(view);
}

export function syncProjectileViewVisuals(
  view: ProjectileView,
  projectile: Projectile,
  displayPosition: Vec2,
  displayFacing: number
): void {
  applyProjectileViewVisualPlan(
    view,
    resolveProjectileViewVisualPlan({ projectile, displayPosition, displayFacing })
  );
}

function createProjectileView(scene: Phaser.Scene, projectile: Projectile): ProjectileView {
  const creationPlan = resolveProjectileViewCreationPlan({ projectile });
  const sprite = scene.add
    .image(
      creationPlan.position.x,
      creationPlan.position.y,
      creationPlan.texture.textureKey,
      creationPlan.texture.frameName
    )
    .setOrigin(creationPlan.origin.x, creationPlan.origin.y)
    .setDepth(creationPlan.depth);

  const view = {
    sprite,
    textureKey: creationPlan.texture.textureKey,
    frameName: creationPlan.texture.frameName
  };
  applyProjectileViewCreationPlan(view, creationPlan);
  syncProjectileViewVisuals(view, projectile, projectile.position, projectile.facing);
  return view;
}

function configureProjectileView(view: ProjectileView, projectile: Projectile): void {
  applyProjectileViewTexturePlan(view, resolveProjectileViewTexturePlan(projectile));
}

function applyProjectileViewCreationPlan(view: ProjectileView, plan: ProjectileViewCreationPlan): void {
  applyProjectileViewTexturePlan(view, plan.texture);
}

function applyProjectileViewActivationPlan(view: ProjectileView, plan: ProjectileViewActivationPlan): void {
  view.sprite.setActive(plan.active).setVisible(plan.visible);
}

function applyProjectileViewTexturePlan(view: ProjectileView, texturePlan: ProjectileViewTexturePlan): void {
  if (
    view.textureKey !== texturePlan.textureKey ||
    view.frameName !== texturePlan.frameName ||
    view.sprite.texture.key !== texturePlan.textureKey
  ) {
    view.sprite.setTexture(texturePlan.textureKey, texturePlan.frameName);
    view.textureKey = texturePlan.textureKey;
    view.frameName = texturePlan.frameName;
  }
  view.sprite.setScale(texturePlan.scale);
  view.sprite.setTint(texturePlan.tint);
}

function applyProjectileViewVisualPlan(view: ProjectileView, plan: ProjectileViewVisualPlan): void {
  view.sprite.setPosition(plan.position.x, plan.position.y);
  view.sprite.setRotation(plan.rotation);
  view.sprite.setAlpha(plan.alpha);
}

function destroyProjectileView(view: ProjectileView): void {
  view.sprite.destroy();
}
