import type Phaser from "phaser";
import type { Vec2 } from "../../../objects/types";

export type FloatingTone = "neutral" | "success" | "warning" | "error";

type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;

interface FloatingTextVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
}

const FLOATING_TEXT_PALETTE: Record<FloatingTone, string> = {
  neutral: "#c4ccd6",
  success: "#7dff9d",
  warning: "#ffd36e",
  error: "#ff9a9a"
};

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
    const label = this.trackTransient(
      this.scene.add
        .text(position.x, position.y - 10, text, {
          fontFamily: "Consolas",
          fontSize: "18px",
          color
        })
        .setOrigin(0.5, 1)
        .setDepth(80)
        .setStroke("#12212b", 3)
    );

    this.scene.tweens.add({
      targets: label,
      y: position.y - 42,
      alpha: 0,
      duration: 620,
      ease: "Cubic.Out",
      onComplete: () => this.destroyTransient(label)
    });
  }

  public showFloatingText(
    position: Vec2,
    text: string,
    tone: FloatingTone = "neutral"
  ): void {
    this.createFloatingText(position, text, FLOATING_TEXT_PALETTE[tone]);
  }
}
