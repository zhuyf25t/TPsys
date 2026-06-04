import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  resolveFloatingTextColor,
  resolveFloatingTextCreationPlan,
  resolveFloatingTextTweenPlan
} from "./functions/FloatingTextVfxRules";
import type {
  DestroyTransient,
  FloatingTextVfxPresenterDependencies,
  FloatingTone,
  TrackTransient
} from "./objects/FloatingTextVfxObjects";

export class FloatingTextVfxPresenter {
  private readonly scene: Phaser.Scene;
  private readonly trackTransient: TrackTransient;
  private readonly destroyTransient: DestroyTransient;

  public constructor({
    scene,
    trackTransient,
    destroyTransient
  }: FloatingTextVfxPresenterDependencies) {
    this.scene = scene;
    this.trackTransient = trackTransient;
    this.destroyTransient = destroyTransient;
  }

  public createFloatingText(position: Vec2, text: string, color: string): void {
    const creationPlan = resolveFloatingTextCreationPlan(position, text, color);
    const tweenPlan = resolveFloatingTextTweenPlan(position);
    const label = this.trackTransient(
      this.scene.add
        .text(creationPlan.position.x, creationPlan.position.y, creationPlan.text, {
          fontFamily: creationPlan.style.fontFamily,
          fontSize: creationPlan.style.fontSize,
          color: creationPlan.style.color
        })
        .setOrigin(creationPlan.style.origin.x, creationPlan.style.origin.y)
        .setDepth(creationPlan.style.depth)
        .setStroke(creationPlan.style.strokeColor, creationPlan.style.strokeThickness)
    );

    this.scene.tweens.add({
      targets: label,
      y: tweenPlan.y,
      alpha: tweenPlan.alpha,
      duration: tweenPlan.durationMs,
      ease: tweenPlan.ease,
      onComplete: () => this.destroyTransient(label)
    });
  }

  public showFloatingText(
    position: Vec2,
    text: string,
    tone: FloatingTone = "neutral"
  ): void {
    this.createFloatingText(position, text, resolveFloatingTextColor(tone));
  }
}
