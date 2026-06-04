import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import Phaser from "phaser";
import {
  resolvePlayerMotionAfterimagePlan,
  resolvePlayerMotionCompletionPulsePlan,
  resolvePlayerMotionJumpArcScale,
  resolvePlayerMotionSpriteTweenPlan,
  resolvePlayerMotionTrailFeedbackPlan,
  resolvePlayerMotionTweenEase
} from "./functions/PlayerMotionTweenRules";
import type {
  PlayerMotionAfterimageShapePlan,
  PlayerMotionTweenControllerOptions,
  PlayerMotionType
} from "./objects/PlayerMotionTweenObjects";

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

  public start(destination: Vec2, durationMs: number, motionType: PlayerMotionType): void {
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
    const trailFeedbackPlan = resolvePlayerMotionTrailFeedbackPlan(motionType);
    this.playerTrailEvent = this.options.scene.time.addEvent({
      delay: trailFeedbackPlan.delayMs,
      loop: true,
      callback: () => {
        this.createAfterimage(
          { x: this.options.playerActor.x, y: this.options.playerActor.y },
          player.facing,
          playerView?.sprite.scaleX ?? baseScale,
          textureKey,
          trailFeedbackPlan.tint,
          trailFeedbackPlan.alpha
        );
      }
    });

    if (motionType === "jump" && playerView) {
      const spriteTweenPlan = resolvePlayerMotionSpriteTweenPlan({ baseScale, durationMs });
      this.options.scene.tweens.add({
        targets: playerView.sprite,
        scaleX: spriteTweenPlan.scaleX,
        scaleY: spriteTweenPlan.scaleY,
        yoyo: spriteTweenPlan.yoyo,
        duration: spriteTweenPlan.durationMs,
        ease: spriteTweenPlan.ease
      });
    }

    this.playerMotionTween = this.options.scene.tweens.add({
      targets: start,
      t: 1,
      duration: durationMs,
      ease: resolvePlayerMotionTweenEase(motionType),
      onUpdate: () => {
        const x = Phaser.Math.Linear(start.x, destination.x, start.t);
        const y = Phaser.Math.Linear(start.y, destination.y, start.t);
        this.options.playerActor.setPosition(x, y);
        player.position = { x, y };

        if (motionType === "jump" && playerView) {
          playerView.sprite.setScale(resolvePlayerMotionJumpArcScale(baseScale, start.t));
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

        const completionPulsePlan = resolvePlayerMotionCompletionPulsePlan(motionType);
        this.options.createPulse(destination, completionPulsePlan.radius, completionPulsePlan.color);
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
    const plan = resolvePlayerMotionAfterimagePlan({
      position,
      rotation,
      scale,
      textureKey,
      tint,
      alpha
    });
    const ghost = createPlayerMotionAfterimage(this.options.scene, plan.shape);
    this.options.scene.tweens.add({
      targets: ghost,
      alpha: plan.tween.alpha,
      scaleX: plan.tween.scaleX,
      scaleY: plan.tween.scaleY,
      duration: plan.tween.durationMs,
      onComplete: () => ghost.destroy()
    });
  }

  public destroy(): void {
    this.stop();
  }
}

function createPlayerMotionAfterimage(
  scene: Phaser.Scene,
  shape: PlayerMotionAfterimageShapePlan
): Phaser.GameObjects.Image {
  const ghost = scene.add
    .image(shape.position.x, shape.position.y, shape.textureKey)
    .setDepth(shape.depth)
    .setRotation(shape.rotation)
    .setScale(shape.scale)
    .setAlpha(shape.alpha);
  ghost.setTint(shape.tint);

  return ghost;
}
