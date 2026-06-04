import type {
  MuzzleAndHitMuzzleBurstRandomSamplingPlan,
  MuzzleAndHitMuzzleBurstSamplingPlanInput,
  MuzzleAndHitMuzzleBurstVfxPlan,
  MuzzleAndHitMuzzleBurstVfxPlanInput,
  MuzzleAndHitHitConfirmVfxPlan,
  MuzzleAndHitHitConfirmVfxPlanInput,
  MuzzleAndHitImpactSparkRandomSamplingPlan,
  MuzzleAndHitImpactSparkVfxPlan,
  MuzzleAndHitImpactSparkVfxPlanInput,
  MuzzleAndHitProjectileDissipateVfxPlan,
  MuzzleAndHitProjectileDissipateVfxPlanInput,
  MuzzleAndHitShockwaveVfxPlan,
  MuzzleAndHitShockwaveVfxPlanInput
} from "../objects/MuzzleAndHitVfxObjects";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

const NORMALIZE_EPSILON = 0.0001;
const HIT_CONFIRM_DEPTH = 82;
const HIT_CONFIRM_OUTER_LINE_WIDTH = 2;
const HIT_CONFIRM_OUTER_ALPHA = 0.92;
const HIT_CONFIRM_INNER_LINE_WIDTH = 1;
const HIT_CONFIRM_INNER_COLOR = 0xffffff;
const HIT_CONFIRM_INNER_ALPHA = 0.58;
const HIT_CONFIRM_FILL_ALPHA = 0.26;
const HIT_CONFIRM_DETAIL_ALPHA = 0.72;
const HIT_CONFIRM_OUTER_RADIUS = 10;
const HIT_CONFIRM_INNER_RADIUS = 5;
const HIT_CONFIRM_FILL_RADIUS = 3;
const HIT_CONFIRM_TWEEN_SCALE = 1.35;
const HIT_CONFIRM_TWEEN_DURATION_MS = 155;
const HIT_CONFIRM_TWEEN_EASE = "Quad.Out";
const IMPACT_SPARK_BURST_RADIUS = 5;
const IMPACT_SPARK_BURST_ALPHA = 0.84;
const IMPACT_SPARK_BURST_DEPTH = 67;
const IMPACT_SPARK_BURST_STROKE_WIDTH = 1;
const IMPACT_SPARK_BURST_STROKE_COLOR = 0xffffff;
const IMPACT_SPARK_BURST_STROKE_ALPHA = 0.46;
const IMPACT_SPARK_BURST_TWEEN_SCALE = 1.8;
const IMPACT_SPARK_BURST_TWEEN_DURATION_MS = 105;
const IMPACT_SPARK_COUNT = 5;
const IMPACT_SPARK_MIN_ANGLE_JITTER_RADIANS = -0.2;
const IMPACT_SPARK_MAX_ANGLE_JITTER_RADIANS = 0.2;
const IMPACT_SPARK_MIN_LENGTH = 7;
const IMPACT_SPARK_MAX_LENGTH = 12;
const IMPACT_SPARK_MIN_TRAVEL_DISTANCE = 14;
const IMPACT_SPARK_MAX_TRAVEL_DISTANCE = 24;
const IMPACT_SPARK_RECTANGLE_HEIGHT = 2;
const IMPACT_SPARK_RECTANGLE_ALPHA = 0.92;
const IMPACT_SPARK_RECTANGLE_DEPTH = 66;
const IMPACT_SPARK_RECTANGLE_ORIGIN = { x: 0, y: 0.5 };
const IMPACT_SPARK_TWEEN_SCALE_X = 0.28;
const IMPACT_SPARK_TWEEN_SCALE_Y = 0.7;
const IMPACT_SPARK_TWEEN_DURATION_MS = 125;
const IMPACT_SPARK_TWEEN_EASE = "Quad.Out";
const PROJECTILE_DISSIPATE_RING_RADIUS = 6;
const PROJECTILE_DISSIPATE_RING_FILL_ALPHA = 0;
const PROJECTILE_DISSIPATE_RING_DEPTH = 65;
const PROJECTILE_DISSIPATE_RING_STROKE_WIDTH = 1;
const PROJECTILE_DISSIPATE_RING_STROKE_ALPHA = 0.34;
const PROJECTILE_DISSIPATE_RING_TWEEN_SCALE = 1.75;
const PROJECTILE_DISSIPATE_RING_TWEEN_DURATION_MS = 130;
const PROJECTILE_DISSIPATE_MOTE_RADIUS = 2;
const PROJECTILE_DISSIPATE_MOTE_FILL_ALPHA = 0.42;
const PROJECTILE_DISSIPATE_MOTE_DEPTH = 66;
const PROJECTILE_DISSIPATE_MOTE_TWEEN_SCALE = 0.3;
const PROJECTILE_DISSIPATE_MOTE_TWEEN_DURATION_MS = 95;
const PROJECTILE_DISSIPATE_TWEEN_EASE = "Quad.Out";
const MUZZLE_BURST_MAX_SPARKS = 8;
const MUZZLE_BURST_CORE_FORWARD_OFFSET = 3;
const MUZZLE_BURST_CORE_MIN_RADIUS = 4;
const MUZZLE_BURST_CORE_RADIUS_SCALE = 0.42;
const MUZZLE_BURST_CORE_FILL_ALPHA = 0.86;
const MUZZLE_BURST_CORE_DEPTH = 67;
const MUZZLE_BURST_CORE_STROKE_WIDTH = 1;
const MUZZLE_BURST_CORE_STROKE_COLOR = 0xffffff;
const MUZZLE_BURST_CORE_STROKE_ALPHA = 0.52;
const MUZZLE_BURST_CORE_TWEEN_SCALE = 1.75;
const MUZZLE_BURST_CORE_TWEEN_DURATION_MS = 95;
const MUZZLE_BURST_FLASH_MIN_WIDTH = 18;
const MUZZLE_BURST_FLASH_WIDTH_SCALE = 1.9;
const MUZZLE_BURST_FLASH_MIN_HEIGHT = 4;
const MUZZLE_BURST_FLASH_HEIGHT_SCALE = 0.48;
const MUZZLE_BURST_FLASH_ALPHA = 0.78;
const MUZZLE_BURST_FLASH_ORIGIN = { x: 0, y: 0.5 };
const MUZZLE_BURST_FLASH_DEPTH = 66;
const MUZZLE_BURST_FLASH_TWEEN_SCALE_X = 0.48;
const MUZZLE_BURST_FLASH_TWEEN_SCALE_Y = 1.6;
const MUZZLE_BURST_FLASH_TWEEN_DURATION_MS = 110;
const MUZZLE_BURST_SPARK_MIN_SPREAD = -0.68;
const MUZZLE_BURST_SPARK_MAX_SPREAD = 0.68;
const MUZZLE_BURST_SPARK_MIN_DISTANCE = 18;
const MUZZLE_BURST_SPARK_MAX_DISTANCE = 34;
const MUZZLE_BURST_SPARK_DISTANCE_RADIUS_SCALE = 0.15;
const MUZZLE_BURST_SPARK_LATERAL_DRIFT_RADIUS_SCALE = 0.28;
const MUZZLE_BURST_SPARK_MIN_LENGTH = 6;
const MUZZLE_BURST_SPARK_MAX_LENGTH = 12;
const MUZZLE_BURST_SPARK_HEIGHT = 2;
const MUZZLE_BURST_SPARK_ALPHA = 0.88;
const MUZZLE_BURST_SPARK_FORWARD_OFFSET = 4;
const MUZZLE_BURST_SPARK_ORIGIN = { x: 0, y: 0.5 };
const MUZZLE_BURST_SPARK_DEPTH = 65;
const MUZZLE_BURST_SPARK_TWEEN_SCALE_X = 0.32;
const MUZZLE_BURST_SPARK_TWEEN_SCALE_Y = 0.76;
const MUZZLE_BURST_SPARK_BASE_DURATION_MS = 150;
const MUZZLE_BURST_SPARK_MIN_DURATION_JITTER_MS = 0;
const MUZZLE_BURST_SPARK_MAX_DURATION_JITTER_MS = 45;
const MUZZLE_BURST_TWEEN_EASE = "Quad.Out";
const SHOCKWAVE_FILL_ALPHA = 0.16;
const SHOCKWAVE_DEPTH = 46;
const SHOCKWAVE_STROKE_WIDTH = 3;
const SHOCKWAVE_STROKE_ALPHA = 0.84;
const SHOCKWAVE_TWEEN_EASE = "Quad.Out";

export function resolveMuzzleAndHitHitConfirmVfxPlan({
  position,
  color
}: MuzzleAndHitHitConfirmVfxPlanInput): MuzzleAndHitHitConfirmVfxPlan {
  return {
    graphics: {
      position,
      depth: HIT_CONFIRM_DEPTH,
      commands: [
        { kind: "lineStyle", width: HIT_CONFIRM_OUTER_LINE_WIDTH, color, alpha: HIT_CONFIRM_OUTER_ALPHA },
        { kind: "strokeCircle", x: 0, y: 0, radius: HIT_CONFIRM_OUTER_RADIUS },
        {
          kind: "lineStyle",
          width: HIT_CONFIRM_INNER_LINE_WIDTH,
          color: HIT_CONFIRM_INNER_COLOR,
          alpha: HIT_CONFIRM_INNER_ALPHA
        },
        { kind: "strokeCircle", x: 0, y: 0, radius: HIT_CONFIRM_INNER_RADIUS },
        { kind: "fillStyle", color, alpha: HIT_CONFIRM_FILL_ALPHA },
        { kind: "fillCircle", x: 0, y: 0, radius: HIT_CONFIRM_FILL_RADIUS },
        { kind: "lineStyle", width: HIT_CONFIRM_INNER_LINE_WIDTH, color, alpha: HIT_CONFIRM_DETAIL_ALPHA },
        { kind: "lineBetween", x1: 0, y1: -15, x2: 4, y2: -11 },
        { kind: "lineBetween", x1: 4, y1: -11, x2: 0, y2: -7 },
        { kind: "lineBetween", x1: 0, y1: -7, x2: -4, y2: -11 },
        { kind: "lineBetween", x1: -4, y1: -11, x2: 0, y2: -15 },
        { kind: "lineStyle", width: HIT_CONFIRM_OUTER_LINE_WIDTH, color, alpha: HIT_CONFIRM_OUTER_ALPHA },
        { kind: "lineBetween", x1: -13, y1: 0, x2: -5, y2: 0 },
        { kind: "lineBetween", x1: 5, y1: 0, x2: 13, y2: 0 },
        { kind: "lineBetween", x1: 0, y1: -13, x2: 0, y2: -5 },
        { kind: "lineBetween", x1: 0, y1: 5, x2: 0, y2: 13 }
      ]
    },
    tween: {
      alpha: 0,
      scale: HIT_CONFIRM_TWEEN_SCALE,
      durationMs: HIT_CONFIRM_TWEEN_DURATION_MS,
      ease: HIT_CONFIRM_TWEEN_EASE
    }
  };
}

export function resolveMuzzleAndHitImpactSparkRandomSamplingPlan(): MuzzleAndHitImpactSparkRandomSamplingPlan {
  return {
    sparkCount: IMPACT_SPARK_COUNT,
    minAngleJitterRadians: IMPACT_SPARK_MIN_ANGLE_JITTER_RADIANS,
    maxAngleJitterRadians: IMPACT_SPARK_MAX_ANGLE_JITTER_RADIANS,
    minSparkLength: IMPACT_SPARK_MIN_LENGTH,
    maxSparkLength: IMPACT_SPARK_MAX_LENGTH,
    minTravelDistance: IMPACT_SPARK_MIN_TRAVEL_DISTANCE,
    maxTravelDistance: IMPACT_SPARK_MAX_TRAVEL_DISTANCE
  };
}

export function resolveMuzzleAndHitImpactSparkVfxPlan({
  position,
  color,
  samples
}: MuzzleAndHitImpactSparkVfxPlanInput): MuzzleAndHitImpactSparkVfxPlan {
  const sparkCount = Math.max(1, samples.length);

  return {
    burst: {
      shape: {
        position,
        radius: IMPACT_SPARK_BURST_RADIUS,
        color,
        fillAlpha: IMPACT_SPARK_BURST_ALPHA,
        depth: IMPACT_SPARK_BURST_DEPTH,
        stroke: {
          width: IMPACT_SPARK_BURST_STROKE_WIDTH,
          color: IMPACT_SPARK_BURST_STROKE_COLOR,
          alpha: IMPACT_SPARK_BURST_STROKE_ALPHA
        }
      },
      tween: {
        alpha: 0,
        scale: IMPACT_SPARK_BURST_TWEEN_SCALE,
        durationMs: IMPACT_SPARK_BURST_TWEEN_DURATION_MS,
        ease: IMPACT_SPARK_TWEEN_EASE
      }
    },
    sparks: samples.map((sample, index) => {
      const angle = (Math.PI * 2 * index) / sparkCount + sample.angleJitterRadians;

      return {
        shape: {
          position,
          width: sample.sparkLength,
          height: IMPACT_SPARK_RECTANGLE_HEIGHT,
          color,
          alpha: IMPACT_SPARK_RECTANGLE_ALPHA,
          origin: IMPACT_SPARK_RECTANGLE_ORIGIN,
          rotation: angle,
          depth: IMPACT_SPARK_RECTANGLE_DEPTH
        },
        tween: {
          x: position.x + Math.cos(angle) * sample.xTravelDistance,
          y: position.y + Math.sin(angle) * sample.yTravelDistance,
          alpha: 0,
          scaleX: IMPACT_SPARK_TWEEN_SCALE_X,
          scaleY: IMPACT_SPARK_TWEEN_SCALE_Y,
          durationMs: IMPACT_SPARK_TWEEN_DURATION_MS,
          ease: IMPACT_SPARK_TWEEN_EASE
        }
      };
    })
  };
}

export function resolveMuzzleAndHitProjectileDissipateVfxPlan({
  position,
  color
}: MuzzleAndHitProjectileDissipateVfxPlanInput): MuzzleAndHitProjectileDissipateVfxPlan {
  return {
    ring: {
      shape: {
        position,
        radius: PROJECTILE_DISSIPATE_RING_RADIUS,
        color,
        fillAlpha: PROJECTILE_DISSIPATE_RING_FILL_ALPHA,
        depth: PROJECTILE_DISSIPATE_RING_DEPTH,
        stroke: {
          width: PROJECTILE_DISSIPATE_RING_STROKE_WIDTH,
          color,
          alpha: PROJECTILE_DISSIPATE_RING_STROKE_ALPHA
        }
      },
      tween: {
        alpha: 0,
        scale: PROJECTILE_DISSIPATE_RING_TWEEN_SCALE,
        durationMs: PROJECTILE_DISSIPATE_RING_TWEEN_DURATION_MS,
        ease: PROJECTILE_DISSIPATE_TWEEN_EASE
      }
    },
    mote: {
      shape: {
        position,
        radius: PROJECTILE_DISSIPATE_MOTE_RADIUS,
        color,
        fillAlpha: PROJECTILE_DISSIPATE_MOTE_FILL_ALPHA,
        depth: PROJECTILE_DISSIPATE_MOTE_DEPTH
      },
      tween: {
        alpha: 0,
        scale: PROJECTILE_DISSIPATE_MOTE_TWEEN_SCALE,
        durationMs: PROJECTILE_DISSIPATE_MOTE_TWEEN_DURATION_MS,
        ease: PROJECTILE_DISSIPATE_TWEEN_EASE
      }
    }
  };
}

export function resolveMuzzleAndHitMuzzleBurstRandomSamplingPlan({
  sparks,
  radius
}: MuzzleAndHitMuzzleBurstSamplingPlanInput): MuzzleAndHitMuzzleBurstRandomSamplingPlan {
  return {
    sparkCount: Math.min(Math.max(0, sparks), MUZZLE_BURST_MAX_SPARKS),
    minSpread: MUZZLE_BURST_SPARK_MIN_SPREAD,
    maxSpread: MUZZLE_BURST_SPARK_MAX_SPREAD,
    minDistance: MUZZLE_BURST_SPARK_MIN_DISTANCE,
    maxDistance: MUZZLE_BURST_SPARK_MAX_DISTANCE,
    distanceRadiusBonus: Math.round(radius * MUZZLE_BURST_SPARK_DISTANCE_RADIUS_SCALE),
    minLateralDrift: -radius * MUZZLE_BURST_SPARK_LATERAL_DRIFT_RADIUS_SCALE,
    maxLateralDrift: radius * MUZZLE_BURST_SPARK_LATERAL_DRIFT_RADIUS_SCALE,
    minSparkLength: MUZZLE_BURST_SPARK_MIN_LENGTH,
    maxSparkLength: MUZZLE_BURST_SPARK_MAX_LENGTH,
    minDurationJitterMs: MUZZLE_BURST_SPARK_MIN_DURATION_JITTER_MS,
    maxDurationJitterMs: MUZZLE_BURST_SPARK_MAX_DURATION_JITTER_MS
  };
}

export function resolveMuzzleAndHitMuzzleBurstVfxPlan({
  position,
  color,
  radius,
  direction,
  samples
}: MuzzleAndHitMuzzleBurstVfxPlanInput): MuzzleAndHitMuzzleBurstVfxPlan {
  const facing = normalizeDirection(direction);
  const perpendicular = perpendicularDirection(facing);
  const rotation = Math.atan2(facing.y, facing.x);

  return {
    ringPulse: {
      position,
      radius,
      color
    },
    core: {
      shape: {
        position: {
          x: position.x + facing.x * MUZZLE_BURST_CORE_FORWARD_OFFSET,
          y: position.y + facing.y * MUZZLE_BURST_CORE_FORWARD_OFFSET
        },
        radius: Math.max(MUZZLE_BURST_CORE_MIN_RADIUS, radius * MUZZLE_BURST_CORE_RADIUS_SCALE),
        color,
        fillAlpha: MUZZLE_BURST_CORE_FILL_ALPHA,
        depth: MUZZLE_BURST_CORE_DEPTH,
        stroke: {
          width: MUZZLE_BURST_CORE_STROKE_WIDTH,
          color: MUZZLE_BURST_CORE_STROKE_COLOR,
          alpha: MUZZLE_BURST_CORE_STROKE_ALPHA
        }
      },
      tween: {
        alpha: 0,
        scale: MUZZLE_BURST_CORE_TWEEN_SCALE,
        durationMs: MUZZLE_BURST_CORE_TWEEN_DURATION_MS,
        ease: MUZZLE_BURST_TWEEN_EASE
      }
    },
    flash: {
      shape: {
        position,
        width: Math.max(MUZZLE_BURST_FLASH_MIN_WIDTH, radius * MUZZLE_BURST_FLASH_WIDTH_SCALE),
        height: Math.max(MUZZLE_BURST_FLASH_MIN_HEIGHT, radius * MUZZLE_BURST_FLASH_HEIGHT_SCALE),
        color,
        alpha: MUZZLE_BURST_FLASH_ALPHA,
        origin: MUZZLE_BURST_FLASH_ORIGIN,
        rotation,
        depth: MUZZLE_BURST_FLASH_DEPTH
      },
      tween: {
        alpha: 0,
        scaleX: MUZZLE_BURST_FLASH_TWEEN_SCALE_X,
        scaleY: MUZZLE_BURST_FLASH_TWEEN_SCALE_Y,
        durationMs: MUZZLE_BURST_FLASH_TWEEN_DURATION_MS,
        ease: MUZZLE_BURST_TWEEN_EASE
      }
    },
    sparks: samples.map((sample) => {
      const sparkDirection = normalizeDirection({
        x: facing.x + perpendicular.x * sample.spread,
        y: facing.y + perpendicular.y * sample.spread
      });
      const sparkAngle = Math.atan2(sparkDirection.y, sparkDirection.x);

      return {
        shape: {
          position: {
            x: position.x + facing.x * MUZZLE_BURST_SPARK_FORWARD_OFFSET,
            y: position.y + facing.y * MUZZLE_BURST_SPARK_FORWARD_OFFSET
          },
          width: sample.sparkLength,
          height: MUZZLE_BURST_SPARK_HEIGHT,
          color,
          alpha: MUZZLE_BURST_SPARK_ALPHA,
          origin: MUZZLE_BURST_SPARK_ORIGIN,
          rotation: sparkAngle,
          depth: MUZZLE_BURST_SPARK_DEPTH
        },
        tween: {
          x: position.x + sparkDirection.x * sample.distance + perpendicular.x * sample.lateralDrift,
          y: position.y + sparkDirection.y * sample.distance + perpendicular.y * sample.lateralDrift,
          alpha: 0,
          scaleX: MUZZLE_BURST_SPARK_TWEEN_SCALE_X,
          scaleY: MUZZLE_BURST_SPARK_TWEEN_SCALE_Y,
          durationMs: MUZZLE_BURST_SPARK_BASE_DURATION_MS + sample.durationJitterMs,
          ease: MUZZLE_BURST_TWEEN_EASE
        }
      };
    })
  };
}

export function resolveMuzzleAndHitShockwaveVfxPlan({
  position,
  startRadius,
  endRadius,
  color,
  durationMs
}: MuzzleAndHitShockwaveVfxPlanInput): MuzzleAndHitShockwaveVfxPlan {
  const scale = endRadius / startRadius;

  return {
    shape: {
      position,
      radius: startRadius,
      color,
      fillAlpha: SHOCKWAVE_FILL_ALPHA,
      depth: SHOCKWAVE_DEPTH,
      strokeWidth: SHOCKWAVE_STROKE_WIDTH,
      strokeColor: color,
      strokeAlpha: SHOCKWAVE_STROKE_ALPHA
    },
    tween: {
      scaleX: scale,
      scaleY: scale,
      alpha: 0,
      durationMs,
      ease: SHOCKWAVE_TWEEN_EASE
    }
  };
}

function normalizeDirection(direction: Vec2): Vec2 {
  const length = Math.hypot(direction.x, direction.y);
  if (length <= NORMALIZE_EPSILON) {
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
