import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";

type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;
type CreateRingPulse = (position: Vec2, radius: number, color: number) => void;

interface MuzzleAndHitVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
  createRingPulse: CreateRingPulse;
}

const MAX_MUZZLE_SPARKS = 8;

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
    const burst = this.trackTransient(
      this.scene.add
        .circle(position.x, position.y, 5, color, 0.84)
        .setDepth(67)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    burst.setStrokeStyle(1, 0xffffff, 0.46);
    this.scene.tweens.add({
      targets: burst,
      alpha: 0,
      scale: 1.8,
      duration: 105,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(burst)
    });

    for (let index = 0; index < 5; index += 1) {
      const angle = (Math.PI * 2 * index) / 5 + Phaser.Math.FloatBetween(-0.2, 0.2);
      const sparkLength = Phaser.Math.Between(7, 12);
      const spark = this.trackTransient(
        this.scene.add
          .rectangle(position.x, position.y, sparkLength, 2, color, 0.92)
          .setOrigin(0, 0.5)
          .setRotation(angle)
          .setDepth(66)
          .setBlendMode(Phaser.BlendModes.ADD)
      );
      this.scene.tweens.add({
        targets: spark,
        x: position.x + Math.cos(angle) * Phaser.Math.Between(14, 24),
        y: position.y + Math.sin(angle) * Phaser.Math.Between(14, 24),
        alpha: 0,
        scaleX: 0.28,
        scaleY: 0.7,
        duration: 125,
        ease: "Quad.Out",
        onComplete: () => this.destroyTransient(spark)
      });
    }
  }

  public createProjectileDissipate(position: Vec2, color: number): void {
    const ring = this.trackTransient(
      this.scene.add
        .circle(position.x, position.y, 6, color, 0)
        .setDepth(65)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    ring.setStrokeStyle(1, color, 0.34);
    this.scene.tweens.add({
      targets: ring,
      alpha: 0,
      scale: 1.75,
      duration: 130,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(ring)
    });

    const mote = this.trackTransient(
      this.scene.add
        .circle(position.x, position.y, 2, color, 0.42)
        .setDepth(66)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    this.scene.tweens.add({
      targets: mote,
      alpha: 0,
      scale: 0.3,
      duration: 95,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(mote)
    });
  }

  public createHitConfirm(position: Vec2, color: number): void {
    const marker = this.trackTransient(this.scene.add.graphics().setDepth(82));
    marker.setPosition(position.x, position.y);
    marker.setBlendMode(Phaser.BlendModes.ADD);
    marker.lineStyle(2, color, 0.92);
    marker.strokeCircle(0, 0, 10);
    marker.lineStyle(1, 0xffffff, 0.58);
    marker.strokeCircle(0, 0, 5);
    marker.fillStyle(color, 0.26);
    marker.fillCircle(0, 0, 3);
    marker.lineStyle(1, color, 0.72);
    marker.lineBetween(0, -15, 4, -11);
    marker.lineBetween(4, -11, 0, -7);
    marker.lineBetween(0, -7, -4, -11);
    marker.lineBetween(-4, -11, 0, -15);
    marker.lineStyle(2, color, 0.92);
    marker.lineBetween(-13, 0, -5, 0);
    marker.lineBetween(5, 0, 13, 0);
    marker.lineBetween(0, -13, 0, -5);
    marker.lineBetween(0, 5, 0, 13);

    this.scene.tweens.add({
      targets: marker,
      alpha: 0,
      scale: 1.35,
      duration: 155,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(marker)
    });
  }

  public createMuzzleBurst(
    position: Vec2,
    color: number,
    radius: number,
    sparks: number,
    direction: Vec2
  ): void {
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const rotation = Math.atan2(facing.y, facing.x);
    this.createRingPulse(position, radius, color);

    const core = this.trackTransient(
      this.scene.add
        .circle(
          position.x + facing.x * 3,
          position.y + facing.y * 3,
          Math.max(4, radius * 0.42),
          color,
          0.86
        )
        .setDepth(67)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    core.setStrokeStyle(1, 0xffffff, 0.52);
    this.scene.tweens.add({
      targets: core,
      alpha: 0,
      scale: 1.75,
      duration: 95,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(core)
    });

    const flash = this.trackTransient(
      this.scene.add
        .rectangle(
          position.x,
          position.y,
          Math.max(18, radius * 1.9),
          Math.max(4, radius * 0.48),
          color,
          0.78
        )
        .setOrigin(0, 0.5)
        .setRotation(rotation)
        .setDepth(66)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    this.scene.tweens.add({
      targets: flash,
      alpha: 0,
      scaleX: 0.48,
      scaleY: 1.6,
      duration: 110,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(flash)
    });

    const sparkCount = Math.min(Math.max(0, sparks), MAX_MUZZLE_SPARKS);
    for (let index = 0; index < sparkCount; index += 1) {
      const spread = Phaser.Math.FloatBetween(-0.68, 0.68);
      const sparkDirection = normalizeDirection({
        x: facing.x + perpendicular.x * spread,
        y: facing.y + perpendicular.y * spread
      });
      const sparkAngle = Math.atan2(sparkDirection.y, sparkDirection.x);
      const distance = Phaser.Math.Between(18, 34) + Math.round(radius * 0.15);
      const lateralDrift = Phaser.Math.FloatBetween(-radius * 0.28, radius * 0.28);
      const spark = this.trackTransient(
        this.scene.add
          .rectangle(
            position.x + facing.x * 4,
            position.y + facing.y * 4,
            Phaser.Math.Between(6, 12),
            2,
            color,
            0.88
          )
          .setOrigin(0, 0.5)
          .setRotation(sparkAngle)
          .setDepth(65)
          .setBlendMode(Phaser.BlendModes.ADD)
      );
      this.scene.tweens.add({
        targets: spark,
        x: position.x + sparkDirection.x * distance + perpendicular.x * lateralDrift,
        y: position.y + sparkDirection.y * distance + perpendicular.y * lateralDrift,
        alpha: 0,
        scaleX: 0.32,
        scaleY: 0.76,
        duration: 150 + Phaser.Math.Between(0, 45),
        ease: "Quad.Out",
        onComplete: () => this.destroyTransient(spark)
      });
    }
  }

  public createShockwave(
    position: Vec2,
    startRadius: number,
    endRadius: number,
    color: number,
    duration: number
  ): void {
    const wave = this.trackTransient(
      this.scene.add.circle(position.x, position.y, startRadius, color, 0.16).setDepth(46)
    );
    wave.setStrokeStyle(3, color, 0.84);
    this.scene.tweens.add({
      targets: wave,
      scaleX: endRadius / startRadius,
      scaleY: endRadius / startRadius,
      alpha: 0,
      duration,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(wave)
    });
  }
}

function normalizeDirection(direction: Vec2): Vec2 {
  const length = Math.hypot(direction.x, direction.y);
  if (length <= 0.0001) {
    return { x: 1, y: 0 };
  }

  return {
    x: direction.x / length,
    y: direction.y / length
  };
}

function perpendicularDirection(direction: Vec2): Vec2 {
  return {
    x: -direction.y,
    y: direction.x
  };
}
