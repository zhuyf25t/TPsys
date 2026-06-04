import type {
  PlayerMotionAfterimagePlan,
  PlayerMotionAfterimagePlanInput,
  PlayerMotionCompletionPulsePlan,
  PlayerMotionSpriteTweenPlan,
  PlayerMotionSpriteTweenPlanInput,
  PlayerMotionTrailFeedbackPlan,
  PlayerMotionType
} from "../objects/PlayerMotionTweenObjects";

const JUMP_TRAIL_DELAY_MS = 42;
const NON_JUMP_TRAIL_DELAY_MS = 28;
const JUMP_TRAIL_TINT = 0xbce8ff;
const DASH_TRAIL_TINT = 0xf4f6ff;
const BLINK_TRAIL_TINT = 0x86dfff;
const JUMP_TRAIL_ALPHA = 0.18;
const NON_JUMP_TRAIL_ALPHA = 0.24;
const JUMP_SPRITE_SCALE_MULTIPLIER = 1.12;
const JUMP_SPRITE_TWEEN_EASE = "Quad.Out";
const BLINK_MOTION_TWEEN_EASE = "Cubic.InOut";
const DEFAULT_MOTION_TWEEN_EASE = "Quad.Out";
const JUMP_ARC_SCALE_AMPLITUDE = 0.07;
const JUMP_COMPLETION_PULSE_RADIUS = 28;
const JUMP_COMPLETION_PULSE_COLOR = 0xc5f3ff;
const DASH_COMPLETION_PULSE_RADIUS = 22;
const DASH_COMPLETION_PULSE_COLOR = 0xdfe8ff;
const BLINK_COMPLETION_PULSE_RADIUS = 44;
const BLINK_COMPLETION_PULSE_COLOR = 0x72e7ff;
const AFTERIMAGE_DEPTH = 41;
const AFTERIMAGE_FADE_SCALE = 0.92;
const AFTERIMAGE_TWEEN_DURATION_MS = 180;

export function resolvePlayerMotionTrailFeedbackPlan(
  motionType: PlayerMotionType
): PlayerMotionTrailFeedbackPlan {
  return {
    delayMs: motionType === "jump" ? JUMP_TRAIL_DELAY_MS : NON_JUMP_TRAIL_DELAY_MS,
    tint:
      motionType === "jump"
        ? JUMP_TRAIL_TINT
        : motionType === "dash"
          ? DASH_TRAIL_TINT
          : BLINK_TRAIL_TINT,
    alpha: motionType === "jump" ? JUMP_TRAIL_ALPHA : NON_JUMP_TRAIL_ALPHA
  };
}

export function resolvePlayerMotionSpriteTweenPlan({
  baseScale,
  durationMs
}: PlayerMotionSpriteTweenPlanInput): PlayerMotionSpriteTweenPlan {
  return {
    scaleX: baseScale * JUMP_SPRITE_SCALE_MULTIPLIER,
    scaleY: baseScale * JUMP_SPRITE_SCALE_MULTIPLIER,
    yoyo: true,
    durationMs: durationMs / 2,
    ease: JUMP_SPRITE_TWEEN_EASE
  };
}

export function resolvePlayerMotionTweenEase(motionType: PlayerMotionType): string {
  return motionType === "blink" ? BLINK_MOTION_TWEEN_EASE : DEFAULT_MOTION_TWEEN_EASE;
}

export function resolvePlayerMotionJumpArcScale(baseScale: number, progress: number): number {
  return baseScale * (1 + Math.sin(progress * Math.PI) * JUMP_ARC_SCALE_AMPLITUDE);
}

export function resolvePlayerMotionCompletionPulsePlan(
  motionType: PlayerMotionType
): PlayerMotionCompletionPulsePlan {
  switch (motionType) {
    case "jump":
      return {
        radius: JUMP_COMPLETION_PULSE_RADIUS,
        color: JUMP_COMPLETION_PULSE_COLOR
      };
    case "dash":
      return {
        radius: DASH_COMPLETION_PULSE_RADIUS,
        color: DASH_COMPLETION_PULSE_COLOR
      };
    case "blink":
      return {
        radius: BLINK_COMPLETION_PULSE_RADIUS,
        color: BLINK_COMPLETION_PULSE_COLOR
      };
  }
}

export function resolvePlayerMotionAfterimagePlan({
  position,
  rotation,
  scale,
  textureKey,
  tint,
  alpha
}: PlayerMotionAfterimagePlanInput): PlayerMotionAfterimagePlan {
  return {
    shape: {
      position,
      rotation,
      scale,
      textureKey,
      tint,
      alpha,
      depth: AFTERIMAGE_DEPTH
    },
    tween: {
      alpha: 0,
      scaleX: scale * AFTERIMAGE_FADE_SCALE,
      scaleY: scale * AFTERIMAGE_FADE_SCALE,
      durationMs: AFTERIMAGE_TWEEN_DURATION_MS
    }
  };
}
