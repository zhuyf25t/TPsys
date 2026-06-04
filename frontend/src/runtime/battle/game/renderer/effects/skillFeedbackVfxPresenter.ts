import Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  resolveSkillBlinkFeedbackVfxPlan,
  resolveSkillDashFeedbackVfxPlan,
  resolveSkillFreezeFeedbackRandomSamplingPlan,
  resolveSkillFreezeFeedbackVfxPlan,
  resolveSkillRejectionFeedbackVfxPlan
} from "./functions/SkillFeedbackVfxRules";
import type {
  DestroyTransient,
  SkillFreezeFeedbackRandomSamplingPlan,
  SkillFreezeFeedbackSample,
  SkillFeedbackGraphicsCommandPlan,
  SkillFeedbackGraphicsVfxPlan,
  SkillFeedbackIntent,
  SkillFeedbackRectangleVfxShapePlan,
  SkillFeedbackTweenPlan,
  SkillFeedbackVfxPresenterDependencies,
  TrackTransient
} from "./objects/SkillFeedbackVfxObjects";

export class SkillFeedbackVfxPresenter {
  private readonly scene: Phaser.Scene;
  private readonly trackTransient: TrackTransient;
  private readonly destroyTransient: DestroyTransient;

  public constructor({
    scene,
    trackTransient,
    destroyTransient
  }: SkillFeedbackVfxPresenterDependencies) {
    this.scene = scene;
    this.trackTransient = trackTransient;
    this.destroyTransient = destroyTransient;
  }

  public createBlinkSkillTargetFeedback(
    position: Vec2,
    intent: SkillFeedbackIntent,
    direction: Vec2
  ): void {
    const plan = resolveSkillBlinkFeedbackVfxPlan({ position, intent, direction });
    const marker = createSkillFeedbackGraphics(this.scene, this.trackTransient, plan.marker.graphics);
    applySkillFeedbackGraphicsCommands(marker, plan.marker.graphics.commands);
    addSkillFeedbackVfxTween(this.scene, this.destroyTransient, marker, plan.marker.tween);
  }

  public createFreezeSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent): void {
    const samplingPlan = resolveSkillFreezeFeedbackRandomSamplingPlan({ intent });
    const plan = resolveSkillFreezeFeedbackVfxPlan({
      position,
      intent,
      samples: this.createFreezeSkillFeedbackSamples(samplingPlan)
    });
    const marker = createSkillFeedbackGraphics(this.scene, this.trackTransient, plan.marker.graphics);
    applySkillFeedbackGraphicsCommands(marker, plan.marker.graphics.commands);
    addSkillFeedbackVfxTween(this.scene, this.destroyTransient, marker, plan.marker.tween);
  }

  public createDashSkillFeedback(position: Vec2, direction: Vec2): void {
    const plan = resolveSkillDashFeedbackVfxPlan({ position, direction });
    const ring = createSkillFeedbackGraphics(this.scene, this.trackTransient, plan.ring.graphics);
    applySkillFeedbackGraphicsCommands(ring, plan.ring.graphics.commands);
    addSkillFeedbackVfxTween(this.scene, this.destroyTransient, ring, plan.ring.tween);

    plan.streaks.forEach((streakPlan) => {
      const streak = createSkillFeedbackVfxRectangle(this.scene, this.trackTransient, streakPlan.shape);
      addSkillFeedbackVfxTween(this.scene, this.destroyTransient, streak, streakPlan.tween);
    });
  }

  public createSkillRejectionFeedback(position: Vec2, radius: number): void {
    const plan = resolveSkillRejectionFeedbackVfxPlan({ position, radius });
    const marker = createSkillFeedbackGraphics(this.scene, this.trackTransient, plan.marker.graphics);
    applySkillFeedbackGraphicsCommands(marker, plan.marker.graphics.commands);
    addSkillFeedbackVfxTween(this.scene, this.destroyTransient, marker, plan.marker.tween);
  }

  private createFreezeSkillFeedbackSamples(
    samplingPlan: SkillFreezeFeedbackRandomSamplingPlan
  ): SkillFreezeFeedbackSample[] {
    return Array.from({ length: samplingPlan.shardCount }, () => ({
      innerRadiusScale: Phaser.Math.FloatBetween(
        samplingPlan.minInnerRadiusScale,
        samplingPlan.maxInnerRadiusScale
      ),
      outerRadiusScale: Phaser.Math.FloatBetween(
        samplingPlan.minOuterRadiusScale,
        samplingPlan.maxOuterRadiusScale
      )
    }));
  }
}

function createSkillFeedbackGraphics(
  scene: Phaser.Scene,
  trackTransient: TrackTransient,
  graphicsPlan: SkillFeedbackGraphicsVfxPlan
): Phaser.GameObjects.Graphics {
  return trackTransient(
    scene.add
      .graphics()
      .setDepth(graphicsPlan.depth)
      .setPosition(graphicsPlan.position.x, graphicsPlan.position.y)
      .setBlendMode(Phaser.BlendModes.ADD)
      .setScale(graphicsPlan.scale)
  );
}

function applySkillFeedbackGraphicsCommands(
  graphics: Phaser.GameObjects.Graphics,
  commands: readonly SkillFeedbackGraphicsCommandPlan[]
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
      case "strokeDiamond":
        strokeDiamond(graphics, command.radius);
        return;
      case "lineBetween":
        graphics.lineBetween(command.x1, command.y1, command.x2, command.y2);
        return;
    }
  });
}

function createSkillFeedbackVfxRectangle(
  scene: Phaser.Scene,
  trackTransient: TrackTransient,
  shape: SkillFeedbackRectangleVfxShapePlan
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

function addSkillFeedbackVfxTween(
  scene: Phaser.Scene,
  destroyTransient: DestroyTransient,
  target: Phaser.GameObjects.GameObject,
  tween: SkillFeedbackTweenPlan
): void {
  const tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig = {
    targets: target,
    duration: tween.durationMs,
    ease: tween.ease,
    onComplete: () => destroyTransient(target)
  };

  applyOptionalSkillFeedbackVfxTweenValues(tweenConfig, tween);
  scene.tweens.add(tweenConfig);
}

function applyOptionalSkillFeedbackVfxTweenValues(
  tweenConfig: Phaser.Types.Tweens.TweenBuilderConfig,
  tween: SkillFeedbackTweenPlan
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
  if (tween.rotation !== undefined) {
    tweenConfig.rotation = tween.rotation;
  }
}

function strokeDiamond(graphics: Phaser.GameObjects.Graphics, radius: number): void {
  graphics.beginPath();
  graphics.moveTo(0, -radius);
  graphics.lineTo(radius, 0);
  graphics.lineTo(0, radius);
  graphics.lineTo(-radius, 0);
  graphics.closePath();
  graphics.strokePath();
}
