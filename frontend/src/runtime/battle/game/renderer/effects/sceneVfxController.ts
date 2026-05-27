import Phaser from "phaser";
import type { Vec2 } from "../../../../../objects/battle/types";
import { FloatingTextVfxPresenter, type FloatingTone } from "./floatingTextVfxPresenter";
import { MuzzleAndHitVfxPresenter } from "./muzzleAndHitVfxPresenter";
import {
  createProjectileTracer as renderProjectileTracer,
  type ProjectileTracerOptions
} from "./projectileTracerVfxRenderer";
import { SkillFeedbackVfxPresenter, type SkillFeedbackIntent } from "./skillFeedbackVfxPresenter";
import { TransientVfxLifecycle } from "./transientVfxLifecycle";

export type { SceneVfxDiagnosticsSnapshot } from "./transientVfxLifecycle";
export type { ProjectileTracerOptions } from "./projectileTracerVfxRenderer";
export type { SkillFeedbackIntent } from "./skillFeedbackVfxPresenter";
export type { FloatingTone } from "./floatingTextVfxPresenter";

interface RingEffect {
  circle: Phaser.GameObjects.Arc;
  ttlMs: number;
  maxTtlMs: number;
}

const DEFAULT_MUZZLE_DIRECTION: Vec2 = { x: 1, y: 0 };

export class SceneVfxController {
  private visualEffects: RingEffect[] = [];
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
    const circle = this.transientVfx.track(
      this.scene.add.circle(position.x, position.y, radius, color, 0.18).setDepth(45)
    );
    circle.setStrokeStyle(2, color, 0.78);
    this.visualEffects.push({ circle, ttlMs: 220, maxTtlMs: 220 });
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

      const nextTtl = effect.ttlMs - deltaMs;
      if (nextTtl <= 0) {
        this.transientVfx.destroyObject(effect.circle);
        continue;
      }

      effect.ttlMs = nextTtl;
      const ttlRatio = nextTtl / effect.maxTtlMs;
      const progress = 1 - ttlRatio;
      effect.circle.setScale(1 + progress * 0.42);
      effect.circle.setAlpha(0.18 * ttlRatio);
      this.visualEffects[writeIndex] = effect;
      writeIndex += 1;
    }

    this.visualEffects.length = writeIndex;
    this.transientVfx.publishDiagnostics();
  };

  public destroy(): void {
    this.transientVfx.destroyAll({ publishDiagnostics: false });
    this.visualEffects = [];
    this.transientVfx.publishDiagnostics();
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
