import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  LocalHeroMotionStreakCreationPlan,
  LocalHeroMotionStreakHiddenPlan,
  LocalHeroMotionStreakRenderPlan,
  LocalHeroMotionStreakUpdate,
  ResolveLocalHeroMotionStreakCreationPlansInput,
  ResolveLocalHeroMotionStreakRenderPlanInput,
  ResolveLocalHeroMotionStreakUpdateInput
} from "../objects/LocalHeroMotionStreakObjects";

const LOCAL_HERO_MOTION_STREAK_COUNT = 3;
const LOCAL_HERO_MOTION_STREAK_DEPTH = 31;
const LOCAL_HERO_MOTION_TINT = 0x8fe8ff;
const LOCAL_HERO_MOTION_STREAK_INITIAL_WIDTH = 18;
const LOCAL_HERO_MOTION_STREAK_WIDTH_STEP = 8;
const LOCAL_HERO_MOTION_STREAK_INITIAL_HEIGHT = 3;
const LOCAL_HERO_MOTION_STREAK_INITIAL_ALPHA = 0;
const LOCAL_HERO_MOTION_STREAK_ORIGIN: Vec2 = { x: 1, y: 0.5 };
const LOCAL_HERO_MOTION_MIN_SPEED = 70;
const LOCAL_HERO_MOTION_MAX_SPEED = 470;
const LOCAL_HERO_MOTION_DECAY = 0.34;
const LOCAL_HERO_MOTION_MIN_FRAME_DISTANCE = 0.05;
const LOCAL_HERO_MOTION_MIN_VISIBLE_INTENSITY = 0.04;

export function resolveLocalHeroMotionStreakCreationPlans({
  position
}: ResolveLocalHeroMotionStreakCreationPlansInput): LocalHeroMotionStreakCreationPlan[] {
  return Array.from({ length: LOCAL_HERO_MOTION_STREAK_COUNT }, (_unused, index) => ({
    position,
    width: LOCAL_HERO_MOTION_STREAK_INITIAL_WIDTH + index * LOCAL_HERO_MOTION_STREAK_WIDTH_STEP,
    height: LOCAL_HERO_MOTION_STREAK_INITIAL_HEIGHT,
    fillColor: LOCAL_HERO_MOTION_TINT,
    fillAlpha: LOCAL_HERO_MOTION_STREAK_INITIAL_ALPHA,
    origin: LOCAL_HERO_MOTION_STREAK_ORIGIN,
    depth: LOCAL_HERO_MOTION_STREAK_DEPTH,
    visible: false
  }));
}

export function resolveLocalHeroMotionStreakUpdate({
  previousPosition,
  displayPosition,
  deltaMs,
  previousAngle,
  previousIntensity
}: ResolveLocalHeroMotionStreakUpdateInput): LocalHeroMotionStreakUpdate {
  const lastPosition = { x: displayPosition.x, y: displayPosition.y };

  if (!previousPosition || !isFiniteVec2(previousPosition)) {
    return {
      lastPosition,
      angle: previousAngle,
      intensity: 0,
      visible: false
    };
  }

  const dx = displayPosition.x - previousPosition.x;
  const dy = displayPosition.y - previousPosition.y;
  const frameDistance = distanceBetween(displayPosition, previousPosition);
  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(1, deltaMs) : 16.67;
  const speed = frameDistance * 1000 / safeDeltaMs;
  const speedIntensity = clamp(
    (speed - LOCAL_HERO_MOTION_MIN_SPEED) / (LOCAL_HERO_MOTION_MAX_SPEED - LOCAL_HERO_MOTION_MIN_SPEED),
    0,
    1
  );

  const nextIntensity =
    speedIntensity > 0 && frameDistance > LOCAL_HERO_MOTION_MIN_FRAME_DISTANCE
      ? speedIntensity
      : previousIntensity * LOCAL_HERO_MOTION_DECAY;
  const nextAngle =
    speedIntensity > 0 && frameDistance > LOCAL_HERO_MOTION_MIN_FRAME_DISTANCE
      ? Math.atan2(dy, dx)
      : previousAngle;

  return {
    lastPosition,
    angle: nextAngle,
    intensity: nextIntensity,
    visible: nextIntensity > LOCAL_HERO_MOTION_MIN_VISIBLE_INTENSITY
  };
}

export function resolveLocalHeroMotionStreakRenderPlan({
  displayPosition,
  angle,
  intensity,
  index
}: ResolveLocalHeroMotionStreakRenderPlanInput): LocalHeroMotionStreakRenderPlan {
  const directionX = Math.cos(angle);
  const directionY = Math.sin(angle);
  const falloff = 1 - index * 0.22;
  const offset = 14 + index * 11 + intensity * 10;
  const sideOffset = (index - 1) * 4;

  return {
    visible: true,
    position: {
      x: displayPosition.x - directionX * offset - directionY * sideOffset,
      y: displayPosition.y - directionY * offset + directionX * sideOffset
    },
    rotation: angle,
    width: 20 + index * 9 + intensity * 14,
    height: 2 + intensity * 2,
    fillColor: LOCAL_HERO_MOTION_TINT,
    alpha: 0.16 * intensity * falloff
  };
}

export function resolveLocalHeroMotionStreakHiddenPlan(): LocalHeroMotionStreakHiddenPlan {
  return {
    visible: false,
    fillColor: LOCAL_HERO_MOTION_TINT,
    fillAlpha: 0
  };
}

export function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
