import Phaser from "phaser";
import type { Vec2 } from "../../../objects/types";

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

export interface ProjectileTracerVfxRendererDependencies {
  scene: Phaser.Scene;
  trackTransient: <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
  destroyTransient: (object: Phaser.GameObjects.GameObject) => void;
}

const DEFAULT_TRACER_DURATION_MS = 120;
const TRACER_GHOST_RADIUS_SCALE = 1.35;

/** 中文名：创建投射物tracer（createProjectileTracer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileTracer(
  dependencies: ProjectileTracerVfxRendererDependencies,
  options: ProjectileTracerOptions
): void {
  const { scene, trackTransient, destroyTransient } = dependencies;
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
      ? trackTransient(
          scene.add
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
  const tracer = trackTransient(
    scene.add
      .rectangle(options.start.x, options.start.y, length, thickness, options.color, alpha)
      .setOrigin(0, 0.5)
      .setRotation(rotation)
      .setDepth(64)
      .setBlendMode(Phaser.BlendModes.ADD)
  );
  const core =
    coreAlphaScale > 0
      ? trackTransient(
          scene.add
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
      ? trackTransient(
          scene.add
            .circle(end.x, end.y, thickness * ghostScale, options.color, alpha * 0.8 * ghostAlphaScale)
            .setDepth(64)
            .setBlendMode(Phaser.BlendModes.ADD)
        )
      : null;

  if (underglow) {
    scene.tweens.add({
      targets: underglow,
      alpha: 0,
      scaleX: 0.82,
      scaleY: 1.25,
      duration: durationMs + 30,
      ease: "Quad.Out",
      onComplete: () => destroyTransient(underglow)
    });
  }
  scene.tweens.add({
    targets: tracer,
    alpha: 0,
    scaleX: 0.72,
    duration: durationMs,
    ease: "Quad.Out",
    onComplete: () => destroyTransient(tracer)
  });
  if (core) {
    scene.tweens.add({
      targets: core,
      alpha: 0,
      scaleX: 0.52,
      duration: Math.max(70, durationMs - 25),
      ease: "Quad.Out",
      onComplete: () => destroyTransient(core)
    });
  }
  if (glintAlphaScale > 0) {
    const glintLength = Math.min(24, Math.max(8, length * 0.26));
    const glintOffset = (Phaser.Math.Between(0, 1) === 0 ? -1 : 1) * Math.max(2, thickness * 1.25);
    const glint = trackTransient(
      scene.add
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
    scene.tweens.add({
      targets: glint,
      alpha: 0,
      scaleX: 0.35,
      duration: Math.max(60, durationMs - 40),
      ease: "Quad.Out",
      onComplete: () => destroyTransient(glint)
    });
  }
  if (ghost) {
    scene.tweens.add({
      targets: ghost,
      alpha: 0,
      scale: 0.45,
      duration: Math.max(80, durationMs - 20),
      ease: "Quad.Out",
      onComplete: () => destroyTransient(ghost)
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
