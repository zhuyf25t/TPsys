import Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { FloatingTextVfxPresenter } from "./floatingTextVfxPresenter";
import { MuzzleAndHitVfxPresenter } from "./muzzleAndHitVfxPresenter";
import type { FloatingTone } from "./objects/FloatingTextVfxObjects";
import type { ProjectileTracerOptions } from "./objects/ProjectileTracerVfxObjects";
import type { SceneRingPulseEffect, SceneRingPulseShapePlan } from "./objects/SceneVfxObjects";
import type { SkillFeedbackIntent } from "./objects/SkillFeedbackVfxObjects";
import {
  resolveSceneRingPulsePlan,
  resolveSceneRingPulseUpdatePlan
} from "./functions/SceneVfxRules";
import { createProjectileTracer as renderProjectileTracer } from "./projectileTracerVfxRenderer";
import { SkillFeedbackVfxPresenter } from "./skillFeedbackVfxPresenter";
import { TransientVfxLifecycle } from "./transientVfxLifecycle";

const DEFAULT_MUZZLE_DIRECTION: Vec2 = { x: 1, y: 0 };

export class SceneVfxController {
  private visualEffects: SceneRingPulseEffect[] = [];
  private readonly transientVfx: TransientVfxLifecycle;
  private readonly muzzleAndHitVfxPresenter: MuzzleAndHitVfxPresenter;
  private readonly skillFeedbackPresenter: SkillFeedbackVfxPresenter;
  private readonly floatingTextPresenter: FloatingTextVfxPresenter;

  public constructor(private readonly scene: Phaser.Scene) {
    this.transientVfx = new TransientVfxLifecycle({
      getActiveRingCount: () => this.countActiveRings()
    });
    this.muzzleAndHitVfxPresenter = new MuzzleAndHitVfxPresenter({
      scene: this.scene,
      trackTransient: (object) => this.transientVfx.track(object),
      destroyTransient: (object) => this.transientVfx.destroyObject(object),
      createRingPulse: (position, radius, color) => this.createPulse(position, radius, color)
    });
    this.skillFeedbackPresenter = new SkillFeedbackVfxPresenter({
      scene: this.scene,
      trackTransient: (object) => this.transientVfx.track(object),
      destroyTransient: (object) => this.transientVfx.destroyObject(object)
    });
    this.floatingTextPresenter = new FloatingTextVfxPresenter({
      scene: this.scene,
      trackTransient: (object) => this.transientVfx.track(object),
      destroyTransient: (object) => this.transientVfx.destroyObject(object)
    });
  }

  public createPulse = (position: Vec2, radius: number, color: number): void => {
    const plan = resolveSceneRingPulsePlan({ position, radius, color });
    const circle = this.transientVfx.track(createSceneRingPulseCircle(this.scene, plan.shape));
    this.visualEffects.push({
      circle,
      ttlMs: plan.lifetime.ttlMs,
      maxTtlMs: plan.lifetime.maxTtlMs
    });
    this.transientVfx.publishDiagnostics();
  };

  public createImpactSpark = (position: Vec2, color: number): void => {
    this.muzzleAndHitVfxPresenter.createImpactSpark(position, color);
  };

  public createProjectileDissipate = (position: Vec2, color: number): void => {
    this.muzzleAndHitVfxPresenter.createProjectileDissipate(position, color);
  };

  public createHitConfirm = (position: Vec2, color: number): void => {
    this.muzzleAndHitVfxPresenter.createHitConfirm(position, color);
  };

  public createBlinkSkillTargetFeedback = (
    position: Vec2,
    intent: SkillFeedbackIntent = "prepare",
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    this.skillFeedbackPresenter.createBlinkSkillTargetFeedback(position, intent, direction);
  };

  public createFreezeSkillTargetFeedback = (
    position: Vec2,
    intent: SkillFeedbackIntent = "prepare"
  ): void => {
    this.skillFeedbackPresenter.createFreezeSkillTargetFeedback(position, intent);
  };

  public createDashSkillFeedback = (
    position: Vec2,
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    this.skillFeedbackPresenter.createDashSkillFeedback(position, direction);
  };

  public createSkillRejectionFeedback = (position: Vec2, radius: number): void => {
    this.skillFeedbackPresenter.createSkillRejectionFeedback(position, radius);
  };

  public createMuzzleBurst = (
    position: Vec2,
    color: number,
    radius: number,
    sparks: number,
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    this.muzzleAndHitVfxPresenter.createMuzzleBurst(position, color, radius, sparks, direction);
  };

  public createShockwave = (position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void => {
    this.muzzleAndHitVfxPresenter.createShockwave(position, startRadius, endRadius, color, duration);
  };

  public createProjectileTracer = (options: ProjectileTracerOptions): void => {
    renderProjectileTracer(
      {
        scene: this.scene,
        trackTransient: (object) => this.transientVfx.track(object),
        destroyTransient: (object) => this.transientVfx.destroyObject(object)
      },
      options
    );
  };

  public createFloatingText = (position: Vec2, text: string, color: string): void => {
    this.floatingTextPresenter.createFloatingText(position, text, color);
  };

  public showFloatingText = (position: Vec2, text: string, tone: FloatingTone = "neutral"): void => {
    this.floatingTextPresenter.showFloatingText(position, text, tone);
  };

  public updateVisualEffects = (deltaMs: number): void => {
    let writeIndex = 0;

    for (let readIndex = 0; readIndex < this.visualEffects.length; readIndex += 1) {
      const effect = this.visualEffects[readIndex];
      if (!effect.circle.active) {
        continue;
      }

      const updatePlan = resolveSceneRingPulseUpdatePlan({
        ttlMs: effect.ttlMs,
        maxTtlMs: effect.maxTtlMs,
        deltaMs
      });
      if (updatePlan.kind === "destroy") {
        this.transientVfx.destroyObject(effect.circle);
        continue;
      }

      effect.ttlMs = updatePlan.ttlMs;
      effect.circle.setScale(updatePlan.scale);
      effect.circle.setAlpha(updatePlan.alpha);
      this.visualEffects[writeIndex] = effect;
      writeIndex += 1;
    }

    this.visualEffects.length = writeIndex;
    this.transientVfx.publishDiagnostics();
  };

  public destroy(): void {
    this.transientVfx.destroyAll({ publishDiagnostics: false });
    this.visualEffects = [];
    this.transientVfx.publishDiagnostics({ force: true });
  }

  private countActiveRings(): number {
    let activeRingCount = 0;
    for (let index = 0; index < this.visualEffects.length; index += 1) {
      if (this.visualEffects[index].circle.active) {
        activeRingCount += 1;
      }
    }

    return activeRingCount;
  }
}

function createSceneRingPulseCircle(
  scene: Phaser.Scene,
  shape: SceneRingPulseShapePlan
): Phaser.GameObjects.Arc {
  const circle = scene.add
    .circle(shape.position.x, shape.position.y, shape.radius, shape.color, shape.fillAlpha)
    .setDepth(shape.depth);
  circle.setStrokeStyle(shape.strokeWidth, shape.strokeColor, shape.strokeAlpha);

  return circle;
}
