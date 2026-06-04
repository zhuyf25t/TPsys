import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  ProjectileTracerOptions,
  ProjectileTracerRectanglePlan,
  ProjectileTracerShapePlan,
  ProjectileTracerVfxPlan,
  ResolveProjectileTracerVfxPlanInput
} from "../objects/ProjectileTracerVfxObjects";

const DEFAULT_TRACER_ALPHA = 0.78;
const DEFAULT_TRACER_DURATION_MS = 120;
const TRACER_GHOST_RADIUS_SCALE = 1.35;
const TRACER_DEFAULT_DIRECTION: Vec2 = { x: 1, y: 0 };
const TRACER_RECTANGLE_ORIGIN: Vec2 = { x: 0, y: 0.5 };
const NORMALIZE_EPSILON = 0.0001;

export function shouldCreateProjectileTracerGlint(options: ProjectileTracerOptions): boolean {
  return clampUnit(options.glintAlphaScale ?? 1) > 0;
}

export function resolveProjectileTracerVfxPlan({
  options,
  glintOffsetDirection
}: ResolveProjectileTracerVfxPlanInput): ProjectileTracerVfxPlan {
  const direction = normalizeDirection(options.direction);
  const length = Math.max(1, options.length);
  const thickness = Math.max(1, options.thickness);
  const durationMs = options.durationMs ?? DEFAULT_TRACER_DURATION_MS;
  const alpha = options.alpha ?? DEFAULT_TRACER_ALPHA;
  const ghostScale = Math.max(0.1, options.ghostScale ?? TRACER_GHOST_RADIUS_SCALE);
  const glintAlphaScale = clampUnit(options.glintAlphaScale ?? 1);
  const underglowAlphaScale = clampUnit(options.underglowAlphaScale ?? 1);
  const coreAlphaScale = clampUnit(options.coreAlphaScale ?? 1);
  const ghostAlphaScale = clampUnit(options.ghostAlphaScale ?? 1);
  const end = {
    x: options.start.x + direction.x * length,
    y: options.start.y + direction.y * length
  };
  const rotation = Math.atan2(direction.y, direction.x);
  const perpendicular = perpendicularDirection(direction);

  return {
    underglow:
      underglowAlphaScale > 0
        ? {
            shape: createTracerRectanglePlan({
              position: options.start,
              width: length,
              height: Math.max(thickness * 3.2, 4),
              color: options.color,
              alpha: alpha * 0.22 * underglowAlphaScale,
              rotation,
              depth: 62
            }),
            tween: {
              alpha: 0,
              scaleX: 0.82,
              scaleY: 1.25,
              durationMs: durationMs + 30,
              ease: "Quad.Out"
            }
          }
        : undefined,
    tracer: {
      shape: createTracerRectanglePlan({
        position: options.start,
        width: length,
        height: thickness,
        color: options.color,
        alpha,
        rotation,
        depth: 64
      }),
      tween: {
        alpha: 0,
        scaleX: 0.72,
        durationMs,
        ease: "Quad.Out"
      }
    },
    core:
      coreAlphaScale > 0
        ? {
            shape: createTracerRectanglePlan({
              position: {
                x: options.start.x + direction.x * (length * 0.12),
                y: options.start.y + direction.y * (length * 0.12)
              },
              width: length * 0.76,
              height: Math.max(1, thickness * 0.38),
              color: 0xffffff,
              alpha: Math.min(0.72, alpha * 0.78) * coreAlphaScale,
              rotation,
              depth: 65
            }),
            tween: {
              alpha: 0,
              scaleX: 0.52,
              durationMs: Math.max(70, durationMs - 25),
              ease: "Quad.Out"
            }
          }
        : undefined,
    ghost:
      ghostAlphaScale > 0
        ? {
            shape: {
              position: end,
              radius: thickness * ghostScale,
              color: options.color,
              alpha: alpha * 0.8 * ghostAlphaScale,
              depth: 64
            },
            tween: {
              alpha: 0,
              scale: 0.45,
              durationMs: Math.max(80, durationMs - 20),
              ease: "Quad.Out"
            }
          }
        : undefined,
    glint:
      glintAlphaScale > 0
        ? createGlintPlan({
            end,
            direction,
            perpendicular,
            length,
            thickness,
            alpha,
            glintAlphaScale,
            glintOffsetDirection,
            rotation,
            durationMs
          })
        : undefined
  };
}

function createGlintPlan(input: {
  end: Vec2;
  direction: Vec2;
  perpendicular: Vec2;
  length: number;
  thickness: number;
  alpha: number;
  glintAlphaScale: number;
  glintOffsetDirection: ResolveProjectileTracerVfxPlanInput["glintOffsetDirection"];
  rotation: number;
  durationMs: number;
}): ProjectileTracerShapePlan<ProjectileTracerRectanglePlan> {
  const glintLength = Math.min(24, Math.max(8, input.length * 0.26));
  const glintOffset = input.glintOffsetDirection * Math.max(2, input.thickness * 1.25);

  return {
    shape: createTracerRectanglePlan({
      position: {
        x: input.end.x - input.direction.x * glintLength + input.perpendicular.x * glintOffset,
        y: input.end.y - input.direction.y * glintLength + input.perpendicular.y * glintOffset
      },
      width: glintLength,
      height: Math.max(1, input.thickness * 0.55),
      color: 0xffffff,
      alpha: Math.min(0.5, input.alpha * 0.58) * input.glintAlphaScale,
      rotation: input.rotation,
      depth: 65
    }),
    tween: {
      alpha: 0,
      scaleX: 0.35,
      durationMs: Math.max(60, input.durationMs - 40),
      ease: "Quad.Out"
    }
  };
}

function createTracerRectanglePlan(input: {
  position: Vec2;
  width: number;
  height: number;
  color: number;
  alpha: number;
  rotation: number;
  depth: number;
}): ProjectileTracerRectanglePlan {
  return {
    position: input.position,
    width: input.width,
    height: input.height,
    color: input.color,
    alpha: input.alpha,
    origin: TRACER_RECTANGLE_ORIGIN,
    rotation: input.rotation,
    depth: input.depth
  };
}

function normalizeDirection(direction: Vec2): Vec2 {
  const length = Math.hypot(direction.x, direction.y);
  if (length <= NORMALIZE_EPSILON) {
    return TRACER_DEFAULT_DIRECTION;
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

function clampUnit(value: number): number {
  return Math.min(1, Math.max(0, value));
}
