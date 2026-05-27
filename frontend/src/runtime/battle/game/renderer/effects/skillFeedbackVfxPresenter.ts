import Phaser from "phaser";
import type { Vec2 } from "../../../../../objects/battle/types";
import { SKILL_DEFINITIONS } from "../../skills";

export type SkillFeedbackIntent = "prepare" | "release";

type TrackTransient = <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
type DestroyTransient = (object: Phaser.GameObjects.GameObject) => void;

interface SkillFeedbackVfxPresenterDependencies {
  scene: Phaser.Scene;
  trackTransient: TrackTransient;
  destroyTransient: DestroyTransient;
}

const BLINK_FEEDBACK_COLOR = 0x7ceaff;
const BLINK_FEEDBACK_CORE_COLOR = 0xf2feff;
const FREEZE_FEEDBACK_COLOR = 0x9bf8ff;
const FREEZE_FEEDBACK_CORE_COLOR = 0xffffff;
const FREEZE_PREPARE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius * 0.32;
const FREEZE_RELEASE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius;
const DASH_FEEDBACK_COLOR = 0xb8d8ff;
const SKILL_REJECT_COLOR = 0xff5a64;

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
    const release = intent === "release";
    const radius = release ? 31 : 24;
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const marker = this.trackTransient(this.scene.add.graphics().setDepth(84));

    marker.setPosition(position.x, position.y);
    marker.setBlendMode(Phaser.BlendModes.ADD);
    marker.setScale(release ? 0.72 : 0.82);
    marker.lineStyle(5, 0x173848, 0.62);
    strokeDiamond(marker, radius * 0.88);
    marker.lineStyle(3, BLINK_FEEDBACK_COLOR, 0.96);
    marker.strokeCircle(0, 0, radius);
    marker.lineStyle(2, BLINK_FEEDBACK_CORE_COLOR, 0.62);
    marker.strokeCircle(0, 0, radius * 0.52);
    marker.lineStyle(3, BLINK_FEEDBACK_COLOR, 0.98);
    strokeDiamond(marker, radius * 0.7);
    marker.lineStyle(2, BLINK_FEEDBACK_CORE_COLOR, 0.7);
    marker.lineBetween(
      -facing.x * radius * 1.35 + perpendicular.x * radius * 0.2,
      -facing.y * radius * 1.35 + perpendicular.y * radius * 0.2,
      -facing.x * radius * 0.56 + perpendicular.x * radius * 0.08,
      -facing.y * radius * 0.56 + perpendicular.y * radius * 0.08
    );
    marker.lineBetween(
      -facing.x * radius * 1.1 - perpendicular.x * radius * 0.28,
      -facing.y * radius * 1.1 - perpendicular.y * radius * 0.28,
      -facing.x * radius * 0.44 - perpendicular.x * radius * 0.12,
      -facing.y * radius * 0.44 - perpendicular.y * radius * 0.12
    );
    marker.fillStyle(BLINK_FEEDBACK_CORE_COLOR, release ? 0.28 : 0.18);
    marker.fillCircle(0, 0, release ? 4 : 3);

    this.scene.tweens.add({
      targets: marker,
      alpha: 0,
      scale: release ? 1.34 : 1.18,
      duration: release ? 230 : 180,
      ease: "Cubic.Out",
      onComplete: () => this.destroyTransient(marker)
    });
  }

  public createFreezeSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent): void {
    const release = intent === "release";
    const radius = release ? FREEZE_RELEASE_FEEDBACK_RADIUS : FREEZE_PREPARE_FEEDBACK_RADIUS;
    const shardCount = release ? 10 : 8;
    const marker = this.trackTransient(this.scene.add.graphics().setDepth(83));

    marker.setPosition(position.x, position.y);
    marker.setBlendMode(Phaser.BlendModes.ADD);
    marker.setScale(release ? 0.72 : 0.74);
    marker.fillStyle(FREEZE_FEEDBACK_COLOR, release ? 0.08 : 0.08);
    marker.fillCircle(0, 0, radius * 0.78);
    marker.lineStyle(5, 0x123a46, 0.5);
    marker.strokeCircle(0, 0, radius);
    marker.lineStyle(3, FREEZE_FEEDBACK_COLOR, 0.92);
    marker.strokeCircle(0, 0, radius);
    marker.lineStyle(1, FREEZE_FEEDBACK_CORE_COLOR, 0.62);
    marker.strokeCircle(0, 0, radius * 0.56);

    for (let index = 0; index < shardCount; index += 1) {
      const angle = (Math.PI * 2 * index) / shardCount + (release ? 0.08 : 0);
      const inner = radius * Phaser.Math.FloatBetween(0.42, 0.58);
      const outer = radius * Phaser.Math.FloatBetween(0.82, 1.08);
      marker.lineStyle(
        index % 2 === 0 ? 2 : 1,
        index % 2 === 0 ? FREEZE_FEEDBACK_COLOR : FREEZE_FEEDBACK_CORE_COLOR,
        0.78
      );
      marker.lineBetween(
        Math.cos(angle) * inner,
        Math.sin(angle) * inner,
        Math.cos(angle) * outer,
        Math.sin(angle) * outer
      );
      marker.lineBetween(
        Math.cos(angle) * outer,
        Math.sin(angle) * outer,
        Math.cos(angle + 0.22) * (outer - 5),
        Math.sin(angle + 0.22) * (outer - 5)
      );
    }

    this.scene.tweens.add({
      targets: marker,
      alpha: 0,
      scale: release ? 1 : 1.12,
      rotation: release ? 0.12 : 0.04,
      duration: release ? 260 : 210,
      ease: "Cubic.Out",
      onComplete: () => this.destroyTransient(marker)
    });
  }

  public createDashSkillFeedback(position: Vec2, direction: Vec2): void {
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const rotation = Math.atan2(facing.y, facing.x);
    const ring = this.trackTransient(this.scene.add.graphics().setDepth(82));

    ring.setPosition(position.x, position.y);
    ring.setBlendMode(Phaser.BlendModes.ADD);
    ring.setScale(0.78);
    ring.lineStyle(4, 0x18334a, 0.48);
    ring.strokeCircle(0, 0, 24);
    ring.lineStyle(2, DASH_FEEDBACK_COLOR, 0.86);
    ring.strokeCircle(0, 0, 20);
    ring.lineStyle(2, 0xffffff, 0.56);
    ring.lineBetween(facing.x * 6, facing.y * 6, facing.x * 20, facing.y * 20);
    ring.lineBetween(
      facing.x * 16 + perpendicular.x * 6,
      facing.y * 16 + perpendicular.y * 6,
      facing.x * 24,
      facing.y * 24
    );
    ring.lineBetween(
      facing.x * 16 - perpendicular.x * 6,
      facing.y * 16 - perpendicular.y * 6,
      facing.x * 24,
      facing.y * 24
    );

    this.scene.tweens.add({
      targets: ring,
      alpha: 0,
      scale: 1.22,
      duration: 160,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(ring)
    });

    [-8, 0, 8].forEach((offset, index) => {
      const length = index === 1 ? 34 : 24;
      const streak = this.trackTransient(
        this.scene.add
          .rectangle(
            position.x - facing.x * 6 + perpendicular.x * offset,
            position.y - facing.y * 6 + perpendicular.y * offset,
            length,
            index === 1 ? 4 : 3,
            DASH_FEEDBACK_COLOR,
            index === 1 ? 0.72 : 0.48
          )
          .setOrigin(1, 0.5)
          .setRotation(rotation)
          .setDepth(81)
          .setBlendMode(Phaser.BlendModes.ADD)
      );
      this.scene.tweens.add({
        targets: streak,
        x: position.x - facing.x * 34 + perpendicular.x * offset,
        y: position.y - facing.y * 34 + perpendicular.y * offset,
        alpha: 0,
        scaleX: 0.32,
        scaleY: 1.35,
        duration: 155 + index * 18,
        ease: "Quad.Out",
        onComplete: () => this.destroyTransient(streak)
      });
    });
  }

  public createSkillRejectionFeedback(position: Vec2, radius: number): void {
    const size = Math.max(16, radius * 0.62);
    const marker = this.trackTransient(this.scene.add.graphics().setDepth(85));

    marker.setPosition(position.x, position.y);
    marker.setBlendMode(Phaser.BlendModes.ADD);
    marker.setScale(0.86);
    marker.lineStyle(5, 0x36141a, 0.58);
    marker.lineBetween(-size, -size, size, size);
    marker.lineBetween(-size, size, size, -size);
    marker.lineStyle(3, SKILL_REJECT_COLOR, 0.96);
    marker.lineBetween(-size, -size, size, size);
    marker.lineBetween(-size, size, size, -size);
    marker.lineStyle(2, 0xffffff, 0.42);
    marker.lineBetween(-size * 0.42, -size * 1.18, -size * 0.1, -size * 0.76);
    marker.lineBetween(size * 0.52, -size * 1.08, size * 0.16, -size * 0.72);
    marker.lineBetween(-size * 1.12, size * 0.18, -size * 0.72, size * 0.06);
    marker.lineBetween(size * 1.1, size * 0.32, size * 0.66, size * 0.12);
    marker.lineStyle(2, SKILL_REJECT_COLOR, 0.72);
    marker.strokeCircle(0, 0, Math.max(8, radius * 0.48));

    this.scene.tweens.add({
      targets: marker,
      alpha: 0,
      scale: 1.14,
      duration: 150,
      ease: "Quad.Out",
      onComplete: () => this.destroyTransient(marker)
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

function strokeDiamond(graphics: Phaser.GameObjects.Graphics, radius: number): void {
  graphics.beginPath();
  graphics.moveTo(0, -radius);
  graphics.lineTo(radius, 0);
  graphics.lineTo(0, radius);
  graphics.lineTo(-radius, 0);
  graphics.closePath();
  graphics.strokePath();
}
