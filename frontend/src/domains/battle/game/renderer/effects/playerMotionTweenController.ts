import Phaser from "phaser";
import type { Hero, Vec2 } from "../../../objects/types";
import type { HeroView } from "../entities/worldViewFactory";

type MotionType = "jump" | "dash" | "blink";

export interface PlayerMotionTweenControllerOptions {
  scene: Phaser.Scene;
  playerActor: Phaser.Physics.Arcade.Image;
  heroViews: Map<string, HeroView>;
  getPlayerHero(): Hero;
  getBaseHeroScale(heroId: string): number;
  createPulse(position: Vec2, radius: number, color: number): void;
}

export class PlayerMotionTweenController {
  private playerMotionTween: Phaser.Tweens.Tween | null = null;
  private playerTrailEvent: Phaser.Time.TimerEvent | null = null;

  public constructor(private readonly options: PlayerMotionTweenControllerOptions) {
    this.options.scene.events.once(Phaser.Scenes.Events.SHUTDOWN, this.destroy, this);
  }

  public isActive(): boolean {
    return this.playerMotionTween !== null;
  }

  public stop(): void {
    this.playerMotionTween?.stop();
    this.playerMotionTween = null;
    this.playerTrailEvent?.remove(false);
    this.playerTrailEvent = null;
    const playerView = this.options.heroViews.get(this.options.getPlayerHero().heroId);
    if (playerView) {
      playerView.sprite.setScale(this.options.getBaseHeroScale(this.options.getPlayerHero().heroId));
    }
  }

  public start(destination: Vec2, durationMs: number, motionType: MotionType): void {
    const player = this.options.getPlayerHero();
    const start = { x: player.position.x, y: player.position.y, t: 0 };
    const playerView = this.options.heroViews.get(player.heroId);
    const baseScale = this.options.getBaseHeroScale(player.heroId);
    const textureKey = playerView?.sprite.texture.key ?? "hero-player";
    const travelDistance = Phaser.Math.Distance.Between(start.x, start.y, destination.x, destination.y);

    if (travelDistance <= 1) {
      return;
    }

    this.stop();
    this.options.playerActor.setVelocity(0, 0);
    this.playerTrailEvent = this.options.scene.time.addEvent({
      delay: motionType === "jump" ? 42 : 28,
      loop: true,
      callback: () => {
        this.createAfterimage(
          { x: this.options.playerActor.x, y: this.options.playerActor.y },
          player.facing,
          playerView?.sprite.scaleX ?? baseScale,
          textureKey,
          motionType === "jump" ? 0xbce8ff : motionType === "dash" ? 0xf4f6ff : 0x86dfff,
          motionType === "jump" ? 0.18 : 0.24
        );
      }
    });

    if (motionType === "jump" && playerView) {
      this.options.scene.tweens.add({
        targets: playerView.sprite,
        scaleX: baseScale * 1.12,
        scaleY: baseScale * 1.12,
        yoyo: true,
        duration: durationMs / 2,
        ease: "Quad.Out"
      });
    }

    this.playerMotionTween = this.options.scene.tweens.add({
      targets: start,
      t: 1,
      duration: durationMs,
      ease: motionType === "blink" ? "Cubic.InOut" : "Quad.Out",
      onUpdate: () => {
        const x = Phaser.Math.Linear(start.x, destination.x, start.t);
        const y = Phaser.Math.Linear(start.y, destination.y, start.t);
        this.options.playerActor.setPosition(x, y);
        player.position = { x, y };

        if (motionType === "jump" && playerView) {
          const arcScale = 1 + Math.sin(start.t * Math.PI) * 0.07;
          playerView.sprite.setScale(baseScale * arcScale);
        }
      },
      onComplete: () => {
        this.playerMotionTween = null;
        this.playerTrailEvent?.remove(false);
        this.playerTrailEvent = null;
        this.options.playerActor.setPosition(destination.x, destination.y);
        player.position = { x: destination.x, y: destination.y };
        if (playerView) {
          playerView.sprite.setScale(baseScale);
        }

        if (motionType === "jump") {
          this.options.createPulse(destination, 28, 0xc5f3ff);
        } else if (motionType === "dash") {
          this.options.createPulse(destination, 22, 0xdfe8ff);
        } else {
          this.options.createPulse(destination, 44, 0x72e7ff);
        }
      }
    });
  }

  public createAfterimage(
    position: Vec2,
    rotation: number,
    scale: number,
    textureKey: string,
    tint: number,
    alpha: number
  ): void {
    const ghost = this.options.scene.add.image(position.x, position.y, textureKey).setDepth(41).setRotation(rotation).setScale(scale).setAlpha(alpha);
    ghost.setTint(tint);
    this.options.scene.tweens.add({
      targets: ghost,
      alpha: 0,
      scaleX: scale * 0.92,
      scaleY: scale * 0.92,
      duration: 180,
      onComplete: () => ghost.destroy()
    });
  }

  public destroy(): void {
    this.stop();
  }
}
