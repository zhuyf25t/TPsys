import type {
  SceneRingPulsePlan,
  SceneRingPulsePlanInput,
  SceneRingPulseUpdatePlan,
  SceneRingPulseUpdatePlanInput
} from "../objects/SceneVfxObjects";

const RING_PULSE_FILL_ALPHA = 0.18;
const RING_PULSE_DEPTH = 45;
const RING_PULSE_STROKE_WIDTH = 2;
const RING_PULSE_STROKE_ALPHA = 0.78;
const RING_PULSE_TTL_MS = 220;
const RING_PULSE_SCALE_GROWTH = 0.42;

export function resolveSceneRingPulsePlan({
  position,
  radius,
  color
}: SceneRingPulsePlanInput): SceneRingPulsePlan {
  return {
    shape: {
      position,
      radius,
      color,
      fillAlpha: RING_PULSE_FILL_ALPHA,
      depth: RING_PULSE_DEPTH,
      strokeWidth: RING_PULSE_STROKE_WIDTH,
      strokeColor: color,
      strokeAlpha: RING_PULSE_STROKE_ALPHA
    },
    lifetime: {
      ttlMs: RING_PULSE_TTL_MS,
      maxTtlMs: RING_PULSE_TTL_MS
    }
  };
}

export function resolveSceneRingPulseUpdatePlan({
  ttlMs,
  maxTtlMs,
  deltaMs
}: SceneRingPulseUpdatePlanInput): SceneRingPulseUpdatePlan {
  const nextTtl = ttlMs - deltaMs;
  if (nextTtl <= 0) {
    return { kind: "destroy" };
  }

  const ttlRatio = nextTtl / maxTtlMs;
  const progress = 1 - ttlRatio;

  return {
    kind: "update",
    ttlMs: nextTtl,
    scale: 1 + progress * RING_PULSE_SCALE_GROWTH,
    alpha: RING_PULSE_FILL_ALPHA * ttlRatio
  };
}
