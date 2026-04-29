import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
import { SKILL_DEFINITIONS } from "../../../../game/skills";
import { TransientVfxLifecycle } from "./transientVfxLifecycle";

export type { SceneVfxDiagnosticsSnapshot } from "./transientVfxLifecycle";

export type FloatingTone = "neutral" | "success" | "warning" | "error";

interface RingEffect {
  circle: Phaser.GameObjects.Arc;
  ttlMs: number;
  maxTtlMs: number;
}

export interface ProjectileTracerOptions {
  start: Vec2;
  direction: Vec2;
  length: number;
  color: number;
  thickness: number;
  durationMs?: number;
  alpha?: number;
  ghostScale?: number;
  glintAlphaScale?: number;
  underglowAlphaScale?: number;
  coreAlphaScale?: number;
  ghostAlphaScale?: number;
}

export type SkillFeedbackIntent = "prepare" | "release";

const DEFAULT_TRACER_DURATION_MS = 120;
const TRACER_GHOST_RADIUS_SCALE = 1.35;
const DEFAULT_MUZZLE_DIRECTION: Vec2 = { x: 1, y: 0 };
const MAX_MUZZLE_SPARKS = 8;
const BLINK_FEEDBACK_COLOR = 0x7ceaff;
const BLINK_FEEDBACK_CORE_COLOR = 0xf2feff;
const FREEZE_FEEDBACK_COLOR = 0x9bf8ff;
const FREEZE_FEEDBACK_CORE_COLOR = 0xffffff;
const FREEZE_PREPARE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius * 0.32;
const FREEZE_RELEASE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius;
const DASH_FEEDBACK_COLOR = 0xb8d8ff;
const SKILL_REJECT_COLOR = 0xff5a64;

export class SceneVfxController {
  private visualEffects: RingEffect[] = [];
  private readonly transientVfx: TransientVfxLifecycle;

  public constructor(private readonly scene: Phaser.Scene) {
    this.transientVfx = new TransientVfxLifecycle({
      getActiveRingCount: () => this.countActiveRings()
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
    const burst = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(burst)
    });

    for (let index = 0; index < 5; index += 1) {
      const angle = (Math.PI * 2 * index) / 5 + Phaser.Math.FloatBetween(-0.2, 0.2);
      const sparkLength = Phaser.Math.Between(7, 12);
      const spark = this.transientVfx.track(
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
        onComplete: () => this.transientVfx.destroyObject(spark)
      });
    }
  };

  public createProjectileDissipate = (position: Vec2, color: number): void => {
    const ring = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(ring)
    });

    const mote = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(mote)
    });
  };

  public createHitConfirm = (position: Vec2, color: number): void => {
    const marker = this.transientVfx.track(this.scene.add.graphics().setDepth(82));
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
      onComplete: () => this.transientVfx.destroyObject(marker)
    });
  };

  public createBlinkSkillTargetFeedback = (
    position: Vec2,
    intent: SkillFeedbackIntent = "prepare",
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    const release = intent === "release";
    const radius = release ? 31 : 24;
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const marker = this.transientVfx.track(this.scene.add.graphics().setDepth(84));

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
      onComplete: () => this.transientVfx.destroyObject(marker)
    });
  };

  public createFreezeSkillTargetFeedback = (
    position: Vec2,
    intent: SkillFeedbackIntent = "prepare"
  ): void => {
    const release = intent === "release";
    const radius = release ? FREEZE_RELEASE_FEEDBACK_RADIUS : FREEZE_PREPARE_FEEDBACK_RADIUS;
    const shardCount = release ? 10 : 8;
    const marker = this.transientVfx.track(this.scene.add.graphics().setDepth(83));

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
      marker.lineStyle(index % 2 === 0 ? 2 : 1, index % 2 === 0 ? FREEZE_FEEDBACK_COLOR : FREEZE_FEEDBACK_CORE_COLOR, 0.78);
      marker.lineBetween(Math.cos(angle) * inner, Math.sin(angle) * inner, Math.cos(angle) * outer, Math.sin(angle) * outer);
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
      onComplete: () => this.transientVfx.destroyObject(marker)
    });
  };

  public createDashSkillFeedback = (
    position: Vec2,
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const rotation = Math.atan2(facing.y, facing.x);
    const ring = this.transientVfx.track(this.scene.add.graphics().setDepth(82));

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
      onComplete: () => this.transientVfx.destroyObject(ring)
    });

    [-8, 0, 8].forEach((offset, index) => {
      const length = index === 1 ? 34 : 24;
      const streak = this.transientVfx.track(
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
        onComplete: () => this.transientVfx.destroyObject(streak)
      });
    });
  };

  public createSkillRejectionFeedback = (position: Vec2, radius: number): void => {
    const size = Math.max(16, radius * 0.62);
    const marker = this.transientVfx.track(this.scene.add.graphics().setDepth(85));

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
      onComplete: () => this.transientVfx.destroyObject(marker)
    });
  };

  public createMuzzleBurst = (
    position: Vec2,
    color: number,
    radius: number,
    sparks: number,
    direction: Vec2 = DEFAULT_MUZZLE_DIRECTION
  ): void => {
    const facing = normalizeDirection(direction);
    const perpendicular = perpendicularDirection(facing);
    const rotation = Math.atan2(facing.y, facing.x);
    this.createPulse(position, radius, color);

    const core = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(core)
    });

    const flash = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(flash)
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
      const spark = this.transientVfx.track(
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
        onComplete: () => this.transientVfx.destroyObject(spark)
      });
    }
  };

  public createShockwave = (position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void => {
    const wave = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(wave)
    });
  };

  public createProjectileTracer = (options: ProjectileTracerOptions): void => {
    const direction = normalizeDirection(options.direction);
    const length = Math.max(1, options.length);
    const thickness = Math.max(1, options.thickness);
    const durationMs = options.durationMs ?? DEFAULT_TRACER_DURATION_MS;
    const alpha = options.alpha ?? 0.78;
    const ghostScale = Math.max(0.1, options.ghostScale ?? TRACER_GHOST_RADIUS_SCALE);
    const glintAlphaScale = Phaser.Math.Clamp(options.glintAlphaScale ?? 1, 0, 1);
    const underglowAlphaScale = Phaser.Math.Clamp(options.underglowAlphaScale ?? 1, 0, 1);
    const coreAlphaScale = Phaser.Math.Clamp(options.coreAlphaScale ?? 1, 0, 1);
    const ghostAlphaScale = Phaser.Math.Clamp(options.ghostAlphaScale ?? 1, 0, 1);
    const end = {
      x: options.start.x + direction.x * length,
      y: options.start.y + direction.y * length
    };
    const rotation = Math.atan2(direction.y, direction.x);
    const perpendicular = perpendicularDirection(direction);
    const underglow =
      underglowAlphaScale > 0
        ? this.transientVfx.track(
            this.scene.add
              .rectangle(
                options.start.x,
                options.start.y,
                length,
                Math.max(thickness * 3.2, 4),
                options.color,
                alpha * 0.22 * underglowAlphaScale
              )
              .setOrigin(0, 0.5)
              .setRotation(rotation)
              .setDepth(62)
              .setBlendMode(Phaser.BlendModes.ADD)
          )
        : null;
    const tracer = this.transientVfx.track(
      this.scene.add
        .rectangle(options.start.x, options.start.y, length, thickness, options.color, alpha)
        .setOrigin(0, 0.5)
        .setRotation(rotation)
        .setDepth(64)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
    const core =
      coreAlphaScale > 0
        ? this.transientVfx.track(
            this.scene.add
              .rectangle(
                options.start.x + direction.x * (length * 0.12),
                options.start.y + direction.y * (length * 0.12),
                length * 0.76,
                Math.max(1, thickness * 0.38),
                0xffffff,
                Math.min(0.72, alpha * 0.78) * coreAlphaScale
              )
              .setOrigin(0, 0.5)
              .setRotation(rotation)
              .setDepth(65)
              .setBlendMode(Phaser.BlendModes.ADD)
          )
        : null;
    const ghost =
      ghostAlphaScale > 0
        ? this.transientVfx.track(
            this.scene.add
              .circle(end.x, end.y, thickness * ghostScale, options.color, alpha * 0.8 * ghostAlphaScale)
              .setDepth(64)
              .setBlendMode(Phaser.BlendModes.ADD)
          )
        : null;

    if (underglow) {
      this.scene.tweens.add({
        targets: underglow,
        alpha: 0,
        scaleX: 0.82,
        scaleY: 1.25,
        duration: durationMs + 30,
        ease: "Quad.Out",
        onComplete: () => this.transientVfx.destroyObject(underglow)
      });
    }
    this.scene.tweens.add({
      targets: tracer,
      alpha: 0,
      scaleX: 0.72,
      duration: durationMs,
      ease: "Quad.Out",
      onComplete: () => this.transientVfx.destroyObject(tracer)
    });
    if (core) {
      this.scene.tweens.add({
        targets: core,
        alpha: 0,
        scaleX: 0.52,
        duration: Math.max(70, durationMs - 25),
        ease: "Quad.Out",
        onComplete: () => this.transientVfx.destroyObject(core)
      });
    }
    if (glintAlphaScale > 0) {
      const glintLength = Math.min(24, Math.max(8, length * 0.26));
      const glintOffset = (Phaser.Math.Between(0, 1) === 0 ? -1 : 1) * Math.max(2, thickness * 1.25);
      const glint = this.transientVfx.track(
        this.scene.add
          .rectangle(
            end.x - direction.x * glintLength + perpendicular.x * glintOffset,
            end.y - direction.y * glintLength + perpendicular.y * glintOffset,
            glintLength,
            Math.max(1, thickness * 0.55),
            0xffffff,
            Math.min(0.5, alpha * 0.58) * glintAlphaScale
          )
          .setOrigin(0, 0.5)
          .setRotation(rotation)
          .setDepth(65)
          .setBlendMode(Phaser.BlendModes.ADD)
      );
      this.scene.tweens.add({
        targets: glint,
        alpha: 0,
        scaleX: 0.35,
        duration: Math.max(60, durationMs - 40),
        ease: "Quad.Out",
        onComplete: () => this.transientVfx.destroyObject(glint)
      });
    }
    if (ghost) {
      this.scene.tweens.add({
        targets: ghost,
        alpha: 0,
        scale: 0.45,
        duration: Math.max(80, durationMs - 20),
        ease: "Quad.Out",
        onComplete: () => this.transientVfx.destroyObject(ghost)
      });
    }
  };

  public createFloatingText = (position: Vec2, text: string, color: string): void => {
    const label = this.transientVfx.track(
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
      onComplete: () => this.transientVfx.destroyObject(label)
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
