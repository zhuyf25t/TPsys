import Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  resolveMuzzleAndHitHitConfirmVfxPlan,
  resolveMuzzleAndHitImpactSparkRandomSamplingPlan,
  resolveMuzzleAndHitImpactSparkVfxPlan,
  resolveMuzzleAndHitMuzzleBurstRandomSamplingPlan,
  resolveMuzzleAndHitMuzzleBurstVfxPlan,
  resolveMuzzleAndHitProjectileDissipateVfxPlan,
  resolveMuzzleAndHitShockwaveVfxPlan
} from "./functions/MuzzleAndHitVfxRules";
import type {
  CreateRingPulse,
  DestroyTransient,
  MuzzleAndHitCircleVfxShapePlan,
  MuzzleAndHitGraphicsCommandPlan,
  MuzzleAndHitGraphicsVfxPlan,
  MuzzleAndHitImpactSparkRandomSamplingPlan,
  MuzzleAndHitImpactSparkSample,
  MuzzleAndHitMuzzleBurstRandomSamplingPlan,
  MuzzleAndHitMuzzleBurstSample,
  MuzzleAndHitRectangleVfxShapePlan,
  MuzzleAndHitTweenPlan,
  MuzzleAndHitVfxPresenterDependencies,
  TrackTransient
} from "./objects/MuzzleAndHitVfxObjects";

export class MuzzleAndHitVfxPresenter {
  private readonly scene: Phaser.Scene;
  private readonly trackTransient: TrackTransient;
  private readonly destroyTransient: DestroyTransient;
  private readonly createRingPulse: CreateRingPulse;

  public constructor({
    scene,
    trackTransient,
    destroyTransient,
    createRingPulse
  }: MuzzleAndHitVfxPresenterDependencies) {
    this.scene = scene;
    this.trackTransient = trackTransient;
    this.destroyTransient = destroyTransient;
    this.createRingPulse = createRingPulse;
  }

  public createImpactSpark(position: Vec2, color: number): void {
    const samplingPlan = resolveMuzzleAndHitImpactSparkRandomSamplingPlan();
    const plan = resolveMuzzleAndHitImpactSparkVfxPlan({
      position,
      color,
      samples: this.createImpactSparkSamples(samplingPlan)
    });
    const burst = createMuzzleAndHitVfxCircle(this.scene, this.trackTransient, plan.burst.shape);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, burst, plan.burst.tween);

    plan.sparks.forEach((sparkPlan) => {
      const spark = createMuzzleAndHitVfxRectangle(this.scene, this.trackTransient, sparkPlan.shape);
      addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, spark, sparkPlan.tween);
    });
  }

  public createProjectileDissipate(position: Vec2, color: number): void {
    const plan = resolveMuzzleAndHitProjectileDissipateVfxPlan({ position, color });
    const ring = createMuzzleAndHitVfxCircle(this.scene, this.trackTransient, plan.ring.shape);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, ring, plan.ring.tween);

    const mote = createMuzzleAndHitVfxCircle(this.scene, this.trackTransient, plan.mote.shape);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, mote, plan.mote.tween);
  }

  public createHitConfirm(position: Vec2, color: number): void {
    const plan = resolveMuzzleAndHitHitConfirmVfxPlan({ position, color });
    const marker = createMuzzleAndHitVfxGraphics(this.scene, this.trackTransient, plan.graphics);
    applyMuzzleAndHitGraphicsCommands(marker, plan.graphics.commands);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, marker, plan.tween);
  }

  public createMuzzleBurst(
    position: Vec2,
    color: number,
    radius: number,
    sparks: number,
    direction: Vec2
  ): void {
    const samplingPlan = resolveMuzzleAndHitMuzzleBurstRandomSamplingPlan({ sparks, radius });
    const plan = resolveMuzzleAndHitMuzzleBurstVfxPlan({
      position,
      color,
      radius,
      direction,
      samples: this.createMuzzleBurstSamples(samplingPlan)
    });
    this.createRingPulse(plan.ringPulse.position, plan.ringPulse.radius, plan.ringPulse.color);

    const core = createMuzzleAndHitVfxCircle(this.scene, this.trackTransient, plan.core.shape);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, core, plan.core.tween);

    const flash = createMuzzleAndHitVfxRectangle(this.scene, this.trackTransient, plan.flash.shape);
    addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, flash, plan.flash.tween);

    plan.sparks.forEach((sparkPlan) => {
      const spark = createMuzzleAndHitVfxRectangle(this.scene, this.trackTransient, sparkPlan.shape);
      addMuzzleAndHitVfxTween(this.scene, this.destroyTransient, spark, sparkPlan.tween);
    });
  }

  public createShockwave(
    position: Vec2,
    startRadius: number,
    endRadius: number,
    color: number,
    duration: number
  ): void {
    const plan = resolveMuzzleAndHitShockwaveVfxPlan({
      position,
      startRadius,
      endRadius,
      color,
      durationMs: duration
    });
    const wave = this.trackTransient(
      this.scene.add
        .circle(
          plan.shape.position.x,
          plan.shape.position.y,
          plan.shape.radius,
          plan.shape.color,
          plan.shape.fillAlpha
        )
        .setDepth(plan.shape.depth)
    );
    wave.setStrokeStyle(plan.shape.strokeWidth, plan.shape.strokeColor, plan.shape.strokeAlpha);
    this.scene.tweens.add({
      targets: wave,
      scaleX: plan.tween.scaleX,
      scaleY: plan.tween.scaleY,
      alpha: plan.tween.alpha,
      duration: plan.tween.durationMs,
      ease: plan.tween.ease,
      onComplete: () => this.destroyTransient(wave)
    });
  }

  private createImpactSparkSamples(
    samplingPlan: MuzzleAndHitImpactSparkRandomSamplingPlan
  ): MuzzleAndHitImpactSparkSample[] {
    return Array.from({ length: samplingPlan.sparkCount }, () => ({
      angleJitterRadians: Phaser.Math.FloatBetween(
        samplingPlan.minAngleJitterRadians,
        samplingPlan.maxAngleJitterRadians
      ),
      sparkLength: Phaser.Math.Between(samplingPlan.minSparkLength, samplingPlan.maxSparkLength),
      xTravelDistance: Phaser.Math.Between(samplingPlan.minTravelDistance, samplingPlan.maxTravelDistance),
      yTravelDistance: Phaser.Math.Between(samplingPlan.minTravelDistance, samplingPlan.maxTravelDistance)
    }));
  }

  private createMuzzleBurstSamples(
    samplingPlan: MuzzleAndHitMuzzleBurstRandomSamplingPlan
  ): MuzzleAndHitMuzzleBurstSample[] {
    return Array.from({ length: samplingPlan.sparkCount }, () => {
      const spread = Phaser.Math.FloatBetween(samplingPlan.minSpread, samplingPlan.maxSpread);
      const distance =
        Phaser.Math.Between(samplingPlan.minDistance, samplingPlan.maxDistance) +
        samplingPlan.distanceRadiusBonus;
      const lateralDrift = Phaser.Math.FloatBetween(
        samplingPlan.minLateralDrift,
        samplingPlan.maxLateralDrift
      );
      const sparkLength = Phaser.Math.Between(samplingPlan.minSparkLength, samplingPlan.maxSparkLength);
      const durationJitterMs = Phaser.Math.Between(
        samplingPlan.minDurationJitterMs,
        samplingPlan.maxDurationJitterMs
      );

      return {
        spread,
        distance,
        lateralDrift,
        sparkLength,
        durationJitterMs
      };
    });
  }
}

function createMuzzleAndHitVfxCircle(
  scene: Phaser.Scene,
  trackTransient: TrackTransient,
  shape: MuzzleAndHitCircleVfxShapePlan
): Phaser.GameObjects.Arc {
  const circle = trackTransient(
    scene.add
      .circle(shape.position.x, shape.position.y, shape.radius, shape.color, shape.fillAlpha)
      .setDepth(shape.depth)
      .setBlendMode(Phaser.BlendModes.ADD)
  );
  if (shape.stroke) {
    circle.setStrokeStyle(shape.stroke.width, shape.stroke.color, shape.stroke.alpha);
  }

  return circle;
}

function createMuzzleAndHitVfxRectangle(
  scene: Phaser.Scene,
  trackTransient: TrackTransient,
  shape: MuzzleAndHitRectangleVfxShapePlan
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

function createMuzzleAndHitVfxGraphics(
  scene: Phaser.Scene,
  trackTransient: TrackTransient,
  graphicsPlan: MuzzleAndHitGraphicsVfxPlan
): Phaser.GameObjects.Graphics {
  return trackTransient(
    scene.add
      .graphics()
      .setDepth(graphicsPlan.depth)
      .setPosition(graphicsPlan.position.x, graphicsPlan.position.y)
      .setBlendMode(Phaser.BlendModes.ADD)
  );
}

function applyMuzzleAndHitGraphicsCommands(
  graphics: Phaser.GameObjects.Graphics,
  commands: readonly MuzzleAndHitGraphicsCommandPlan[]
): void {
  commands.forEach((command) => {
    switch (command.kind) {
      case "lineStyle":
        graphics.lineStyle(command.width, command.color, command.alpha);
        return;
      case "fillStyle":
        graphics.fillStyle(command.color, command.alpha);
        return;
      case "strokeCircle":
        graphics.strokeCircle(command.x, command.y, command.radius);
        return;
      case "fillCircle":
        graphics.fillCircle(command.x, command.y, command.radius);
        return;
      case "lineBetween":
        graphics.lineBetween(command.x1, command.y1, command.x2, command.y2);
        return;
    }
  });
}

function addMuzzleAndHitVfxTween(
  scene: Phaser.Scene,
  destroyTransient: DestroyTransient,
  target: Phaser.GameObjects.GameObject,
  tween: MuzzleAndHitTweenPlan
): void {
  const tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig = {
    targets: target,
    duration: tween.durationMs,
    ease: tween.ease,
    onComplete: () => destroyTransient(target)
  };

  applyOptionalMuzzleAndHitVfxTweenValues(tweenConfig, tween);
  scene.tweens.add(tweenConfig);
}

function applyOptionalMuzzleAndHitVfxTweenValues(
  tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig,
  tween: MuzzleAndHitTweenPlan
): void {
  if (tween.x !== undefined) {
    tweenConfig.x = tween.x;
  }
  if (tween.y !== undefined) {
    tweenConfig.y = tween.y;
  }
  if (tween.alpha !== undefined) {
    tweenConfig.alpha = tween.alpha;
  }
  if (tween.scale !== undefined) {
    tweenConfig.scale = tween.scale;
  }
  if (tween.scaleX !== undefined) {
    tweenConfig.scaleX = tween.scaleX;
  }
  if (tween.scaleY !== undefined) {
    tweenConfig.scaleY = tween.scaleY;
  }
}
