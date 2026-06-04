import Phaser from "phaser";
import {
  resolveProjectileTracerVfxPlan,
  shouldCreateProjectileTracerGlint
} from "./functions/ProjectileTracerVfxRules";
import type {
  ProjectileTracerCirclePlan,
  ProjectileTracerGlintOffsetDirection,
  ProjectileTracerOptions,
  ProjectileTracerRectanglePlan,
  ProjectileTracerTweenPlan,
  ProjectileTracerVfxRendererDependencies
} from "./objects/ProjectileTracerVfxObjects";

/** Creates the projectile tracer VFX while keeping numeric planning in the effects functions boundary. */
export function createProjectileTracer(
  dependencies: ProjectileTracerVfxRendererDependencies,
  options: ProjectileTracerOptions
): void {
  const { scene, trackTransient, destroyTransient } = dependencies;
  const glintOffsetDirection = chooseProjectileTracerGlintOffsetDirection(options);
  const plan = resolveProjectileTracerVfxPlan({ options, glintOffsetDirection });
  const underglow = plan.underglow
    ? createProjectileTracerRectangle(scene, trackTransient, plan.underglow.shape)
    : null;
  const tracer = createProjectileTracerRectangle(scene, trackTransient, plan.tracer.shape);
  const core = plan.core ? createProjectileTracerRectangle(scene, trackTransient, plan.core.shape) : null;
  const ghost = plan.ghost ? createProjectileTracerCircle(scene, trackTransient, plan.ghost.shape) : null;

  if (underglow && plan.underglow) {
    addProjectileTracerTween(scene, destroyTransient, underglow, plan.underglow.tween);
  }
  addProjectileTracerTween(scene, destroyTransient, tracer, plan.tracer.tween);
  if (core && plan.core) {
    addProjectileTracerTween(scene, destroyTransient, core, plan.core.tween);
  }
  if (plan.glint) {
    const glint = createProjectileTracerRectangle(scene, trackTransient, plan.glint.shape);
    addProjectileTracerTween(scene, destroyTransient, glint, plan.glint.tween);
  }
  if (ghost && plan.ghost) {
    addProjectileTracerTween(scene, destroyTransient, ghost, plan.ghost.tween);
  }
}

function chooseProjectileTracerGlintOffsetDirection(
  options: ProjectileTracerOptions
): ProjectileTracerGlintOffsetDirection {
  if (!shouldCreateProjectileTracerGlint(options)) {
    return 1;
  }

  return Phaser.Math.Between(0, 1) === 0 ? -1 : 1;
}

function createProjectileTracerRectangle(
  scene: Phaser.Scene,
  trackTransient: ProjectileTracerVfxRendererDependencies["trackTransient"],
  shape: ProjectileTracerRectanglePlan
): Phaser.GameObjects.Rectangle {
  return trackTransient(
    scene.add
      .rectangle(shape.position.x, shape.position.y, shape.width, shape.height, shape.color, shape.alpha)
      .setOrigin(shape.origin.x, shape.origin.y)
      .setRotation(shape.rotation)
      .setDepth(shape.depth)
      .setBlendMode(Phaser.BlendModes.ADD)
  );
}

function createProjectileTracerCircle(
  scene: Phaser.Scene,
  trackTransient: ProjectileTracerVfxRendererDependencies["trackTransient"],
  shape: ProjectileTracerCirclePlan
): Phaser.GameObjects.Arc {
  return trackTransient(
    scene.add
      .circle(shape.position.x, shape.position.y, shape.radius, shape.color, shape.alpha)
      .setDepth(shape.depth)
      .setBlendMode(Phaser.BlendModes.ADD)
  );
}

function addProjectileTracerTween(
  scene: Phaser.Scene,
  destroyTransient: ProjectileTracerVfxRendererDependencies["destroyTransient"],
  target: Phaser.GameObjects.GameObject,
  tween: ProjectileTracerTweenPlan
): void {
  const tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig = {
    targets: target,
    alpha: tween.alpha,
    duration: tween.durationMs,
    ease: tween.ease,
    onComplete: () => destroyTransient(target)
  };

  applyOptionalTweenScales(tweenConfig, tween);
  scene.tweens.add(tweenConfig);
}

function applyOptionalTweenScales(
  tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig,
  tween: ProjectileTracerTweenPlan
): void {
  if (tween.scaleX !== undefined) {
    tweenConfig.scaleX = tween.scaleX;
  }
  if (tween.scaleY !== undefined) {
    tweenConfig.scaleY = tween.scaleY;
  }
  if (tween.scale !== undefined) {
    tweenConfig.scale = tween.scale;
  }
}
