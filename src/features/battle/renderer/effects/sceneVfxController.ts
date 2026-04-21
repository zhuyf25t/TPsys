import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";

export type FloatingTone = "neutral" | "success" | "warning" | "error";

interface RingEffect {
  circle: Phaser.GameObjects.Arc;
  ttlMs: number;
  maxTtlMs: number;
}

export class SceneVfxController {
  private visualEffects: RingEffect[] = [];

  public constructor(private readonly scene: Phaser.Scene) {}

  public createPulse = (position: Vec2, radius: number, color: number): void => {
    const circle = this.scene.add.circle(position.x, position.y, radius, color, 0.18).setDepth(45);
    circle.setStrokeStyle(2, color, 0.78);
    this.visualEffects.push({ circle, ttlMs: 220, maxTtlMs: 220 });
  };

  public createImpactSpark = (position: Vec2, color: number): void => {
    for (let index = 0; index < 4; index += 1) {
      const angle = (Math.PI * 2 * index) / 4 + Phaser.Math.FloatBetween(-0.18, 0.18);
      const spark = this.scene.add.circle(position.x, position.y, 3, color, 0.95).setDepth(66);
      this.scene.tweens.add({
        targets: spark,
        x: position.x + Math.cos(angle) * Phaser.Math.Between(12, 20),
        y: position.y + Math.sin(angle) * Phaser.Math.Between(12, 20),
        alpha: 0,
        scale: 0.2,
        duration: 120,
        onComplete: () => spark.destroy()
      });
    }
  };

  public createMuzzleBurst = (position: Vec2, color: number, radius: number, sparks: number): void => {
    this.createPulse(position, radius, color);
    for (let index = 0; index < sparks; index += 1) {
      const angle = Phaser.Math.FloatBetween(-0.75, 0.75);
      const smoke = this.scene.add.circle(position.x, position.y, Phaser.Math.Between(3, 5), color, 0.65).setDepth(64);
      this.scene.tweens.add({
        targets: smoke,
        x: position.x + Math.cos(angle) * Phaser.Math.Between(18, 34),
        y: position.y + Math.sin(angle) * Phaser.Math.Between(10, 24),
        alpha: 0,
        scale: 1.7,
        duration: 180,
        onComplete: () => smoke.destroy()
      });
    }
  };

  public createShockwave = (position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void => {
    const wave = this.scene.add.circle(position.x, position.y, startRadius, color, 0.16).setDepth(46);
    wave.setStrokeStyle(3, color, 0.84);
    this.scene.tweens.add({
      targets: wave,
      scaleX: endRadius / startRadius,
      scaleY: endRadius / startRadius,
      alpha: 0,
      duration,
      ease: "Quad.Out",
      onComplete: () => wave.destroy()
    });
  };

  public createFloatingText = (position: Vec2, text: string, color: string): void => {
    const label = this.scene
      .add
      .text(position.x, position.y - 10, text, {
        fontFamily: "Consolas",
        fontSize: "18px",
        color
      })
      .setOrigin(0.5, 1)
      .setDepth(80)
      .setStroke("#12212b", 3);

    this.scene.tweens.add({
      targets: label,
      y: position.y - 42,
      alpha: 0,
      duration: 620,
      ease: "Cubic.Out",
      onComplete: () => label.destroy()
    });
  };

  public showFloatingText = (position: Vec2, text: string, tone: FloatingTone = "neutral"): void => {
    const palette: Record<FloatingTone, string> = {
      neutral: "#c4ccd6",
      success: "#7dff9d",
      warning: "#ffd36e",
      error: "#ff9a9a"
    };

    this.createFloatingText(position, text, palette[tone]);
  };

  public updateVisualEffects = (deltaMs: number): void => {
    const remaining: RingEffect[] = [];

    this.visualEffects.forEach((effect) => {
      const nextTtl = effect.ttlMs - deltaMs;
      if (nextTtl <= 0) {
        effect.circle.destroy();
        return;
      }

      const progress = 1 - nextTtl / effect.maxTtlMs;
      effect.circle.setScale(1 + progress * 0.18);
      effect.circle.setAlpha(0.18 * (nextTtl / effect.maxTtlMs));
      remaining.push({ ...effect, ttlMs: nextTtl });
    });

    this.visualEffects = remaining;
  };

  public destroy(): void {
    this.visualEffects.forEach((effect) => effect.circle.destroy());
    this.visualEffects = [];
  }
}
