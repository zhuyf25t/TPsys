import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import { SKILL_DEFINITIONS } from "../../../../../../objects/battle/microservices/abilities/objects/abilities/BattleAbilityRuleDefinitions";
import type {
  SkillBlinkFeedbackVfxPlan,
  SkillBlinkFeedbackVfxPlanInput,
  SkillDashFeedbackVfxPlan,
  SkillDashFeedbackVfxPlanInput,
  SkillFreezeFeedbackRandomSamplingPlan,
  SkillFreezeFeedbackRandomSamplingPlanInput,
  SkillFreezeFeedbackVfxPlan,
  SkillFreezeFeedbackVfxPlanInput,
  SkillRejectionFeedbackVfxPlan,
  SkillRejectionFeedbackVfxPlanInput
} from "../objects/SkillFeedbackVfxObjects";

const NORMALIZE_EPSILON = 0.0001;
const BLINK_FEEDBACK_COLOR = 0x7ceaff;
const BLINK_FEEDBACK_CORE_COLOR = 0xf2feff;
const BLINK_MARKER_DEPTH = 84;
const BLINK_PREPARE_RADIUS = 24;
const BLINK_RELEASE_RADIUS = 31;
const BLINK_PREPARE_INITIAL_SCALE = 0.82;
const BLINK_RELEASE_INITIAL_SCALE = 0.72;
const BLINK_OUTLINE_WIDTH = 5;
const BLINK_OUTLINE_COLOR = 0x173848;
const BLINK_OUTLINE_ALPHA = 0.62;
const BLINK_OUTLINE_DIAMOND_RADIUS_SCALE = 0.88;
const BLINK_OUTER_WIDTH = 3;
const BLINK_OUTER_ALPHA = 0.96;
const BLINK_CORE_RING_WIDTH = 2;
const BLINK_CORE_RING_ALPHA = 0.62;
const BLINK_CORE_RING_RADIUS_SCALE = 0.52;
const BLINK_INNER_DIAMOND_WIDTH = 3;
const BLINK_INNER_DIAMOND_ALPHA = 0.98;
const BLINK_INNER_DIAMOND_RADIUS_SCALE = 0.7;
const BLINK_TRAIL_WIDTH = 2;
const BLINK_TRAIL_ALPHA = 0.7;
const BLINK_TRAIL_A_BACK_DISTANCE = 1.35;
const BLINK_TRAIL_A_SIDE_DISTANCE = 0.2;
const BLINK_TRAIL_A_END_DISTANCE = 0.56;
const BLINK_TRAIL_A_END_SIDE_DISTANCE = 0.08;
const BLINK_TRAIL_B_BACK_DISTANCE = 1.1;
const BLINK_TRAIL_B_SIDE_DISTANCE = 0.28;
const BLINK_TRAIL_B_END_DISTANCE = 0.44;
const BLINK_TRAIL_B_END_SIDE_DISTANCE = 0.12;
const BLINK_PREPARE_CORE_FILL_ALPHA = 0.18;
const BLINK_RELEASE_CORE_FILL_ALPHA = 0.28;
const BLINK_PREPARE_CORE_FILL_RADIUS = 3;
const BLINK_RELEASE_CORE_FILL_RADIUS = 4;
const BLINK_PREPARE_TWEEN_SCALE = 1.18;
const BLINK_RELEASE_TWEEN_SCALE = 1.34;
const BLINK_PREPARE_TWEEN_DURATION_MS = 180;
const BLINK_RELEASE_TWEEN_DURATION_MS = 230;
const BLINK_TWEEN_EASE = "Cubic.Out";
const DASH_FEEDBACK_COLOR = 0xb8d8ff;
const DASH_RING_DEPTH = 82;
const DASH_RING_INITIAL_SCALE = 0.78;
const DASH_RING_OUTLINE_WIDTH = 4;
const DASH_RING_OUTLINE_COLOR = 0x18334a;
const DASH_RING_OUTLINE_ALPHA = 0.48;
const DASH_RING_OUTER_WIDTH = 2;
const DASH_RING_OUTER_RADIUS = 20;
const DASH_RING_OUTER_ALPHA = 0.86;
const DASH_RING_INNER_RADIUS = 24;
const DASH_RING_CORE_WIDTH = 2;
const DASH_RING_CORE_COLOR = 0xffffff;
const DASH_RING_CORE_ALPHA = 0.56;
const DASH_RING_ARROW_START_DISTANCE = 6;
const DASH_RING_ARROW_MID_DISTANCE = 16;
const DASH_RING_ARROW_END_DISTANCE = 20;
const DASH_RING_ARROW_HEAD_DISTANCE = 24;
const DASH_RING_ARROW_HEAD_WIDTH = 6;
const DASH_RING_TWEEN_SCALE = 1.22;
const DASH_RING_TWEEN_DURATION_MS = 160;
const DASH_STREAK_DEPTH = 81;
const DASH_STREAK_START_BACK_DISTANCE = 6;
const DASH_STREAK_END_BACK_DISTANCE = 34;
const DASH_STREAK_SIDE_LENGTH = 24;
const DASH_STREAK_CENTER_LENGTH = 34;
const DASH_STREAK_SIDE_HEIGHT = 3;
const DASH_STREAK_CENTER_HEIGHT = 4;
const DASH_STREAK_SIDE_ALPHA = 0.48;
const DASH_STREAK_CENTER_ALPHA = 0.72;
const DASH_STREAK_ORIGIN: Vec2 = { x: 1, y: 0.5 };
const DASH_STREAK_TWEEN_SCALE_X = 0.32;
const DASH_STREAK_TWEEN_SCALE_Y = 1.35;
const DASH_STREAK_BASE_TWEEN_DURATION_MS = 155;
const DASH_STREAK_TWEEN_DURATION_STEP_MS = 18;
const DASH_TWEEN_EASE = "Quad.Out";
const DASH_STREAK_OFFSETS = [-8, 0, 8] as const;
const FREEZE_FEEDBACK_COLOR = 0x9bf8ff;
const FREEZE_FEEDBACK_CORE_COLOR = 0xffffff;
const FREEZE_PREPARE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius * 0.32;
const FREEZE_RELEASE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius;
const FREEZE_MARKER_DEPTH = 83;
const FREEZE_PREPARE_INITIAL_SCALE = 0.74;
const FREEZE_RELEASE_INITIAL_SCALE = 0.72;
const FREEZE_FILL_ALPHA = 0.08;
const FREEZE_FILL_RADIUS_SCALE = 0.78;
const FREEZE_OUTLINE_WIDTH = 5;
const FREEZE_OUTLINE_COLOR = 0x123a46;
const FREEZE_OUTLINE_ALPHA = 0.5;
const FREEZE_OUTER_WIDTH = 3;
const FREEZE_OUTER_ALPHA = 0.92;
const FREEZE_CORE_RING_WIDTH = 1;
const FREEZE_CORE_RING_ALPHA = 0.62;
const FREEZE_CORE_RING_RADIUS_SCALE = 0.56;
const FREEZE_RELEASE_SHARD_COUNT = 10;
const FREEZE_PREPARE_SHARD_COUNT = 8;
const FREEZE_RELEASE_SHARD_ANGLE_OFFSET = 0.08;
const FREEZE_PREPARE_SHARD_ANGLE_OFFSET = 0;
const FREEZE_SHARD_MIN_INNER_RADIUS_SCALE = 0.42;
const FREEZE_SHARD_MAX_INNER_RADIUS_SCALE = 0.58;
const FREEZE_SHARD_MIN_OUTER_RADIUS_SCALE = 0.82;
const FREEZE_SHARD_MAX_OUTER_RADIUS_SCALE = 1.08;
const FREEZE_SHARD_EVEN_WIDTH = 2;
const FREEZE_SHARD_ODD_WIDTH = 1;
const FREEZE_SHARD_ALPHA = 0.78;
const FREEZE_SHARD_BRANCH_ANGLE_OFFSET = 0.22;
const FREEZE_SHARD_BRANCH_OUTER_OFFSET = 5;
const FREEZE_PREPARE_TWEEN_SCALE = 1.12;
const FREEZE_RELEASE_TWEEN_SCALE = 1;
const FREEZE_PREPARE_TWEEN_ROTATION = 0.04;
const FREEZE_RELEASE_TWEEN_ROTATION = 0.12;
const FREEZE_PREPARE_TWEEN_DURATION_MS = 210;
const FREEZE_RELEASE_TWEEN_DURATION_MS = 260;
const FREEZE_TWEEN_EASE = "Cubic.Out";
const REJECTION_MARKER_DEPTH = 85;
const REJECTION_INITIAL_SCALE = 0.86;
const REJECTION_MIN_SIZE = 16;
const REJECTION_RADIUS_SCALE = 0.62;
const REJECTION_SHADOW_WIDTH = 5;
const REJECTION_SHADOW_COLOR = 0x36141a;
const REJECTION_SHADOW_ALPHA = 0.58;
const REJECTION_COLOR = 0xff5a64;
const REJECTION_CROSS_WIDTH = 3;
const REJECTION_CROSS_ALPHA = 0.96;
const REJECTION_HIGHLIGHT_WIDTH = 2;
const REJECTION_HIGHLIGHT_COLOR = 0xffffff;
const REJECTION_HIGHLIGHT_ALPHA = 0.42;
const REJECTION_ACCENT_WIDTH = 2;
const REJECTION_ACCENT_ALPHA = 0.72;
const REJECTION_CIRCLE_MIN_RADIUS = 8;
const REJECTION_CIRCLE_RADIUS_SCALE = 0.48;
const REJECTION_TWEEN_SCALE = 1.14;
const REJECTION_TWEEN_DURATION_MS = 150;
const REJECTION_TWEEN_EASE = "Quad.Out";

export function resolveSkillBlinkFeedbackVfxPlan({
  position,
  intent,
  direction
}: SkillBlinkFeedbackVfxPlanInput): SkillBlinkFeedbackVfxPlan {
  const release = intent === "release";
  const radius = release ? BLINK_RELEASE_RADIUS : BLINK_PREPARE_RADIUS;
  const facing = normalizeDirection(direction);
  const perpendicular = perpendicularDirection(facing);

  return {
    marker: {
      graphics: {
        position,
        depth: BLINK_MARKER_DEPTH,
        scale: release ? BLINK_RELEASE_INITIAL_SCALE : BLINK_PREPARE_INITIAL_SCALE,
        commands: [
          {
            kind: "lineStyle",
            width: BLINK_OUTLINE_WIDTH,
            color: BLINK_OUTLINE_COLOR,
            alpha: BLINK_OUTLINE_ALPHA
          },
          {
            kind: "strokeDiamond",
            radius: radius * BLINK_OUTLINE_DIAMOND_RADIUS_SCALE
          },
          {
            kind: "lineStyle",
            width: BLINK_OUTER_WIDTH,
            color: BLINK_FEEDBACK_COLOR,
            alpha: BLINK_OUTER_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius
          },
          {
            kind: "lineStyle",
            width: BLINK_CORE_RING_WIDTH,
            color: BLINK_FEEDBACK_CORE_COLOR,
            alpha: BLINK_CORE_RING_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius: radius * BLINK_CORE_RING_RADIUS_SCALE
          },
          {
            kind: "lineStyle",
            width: BLINK_INNER_DIAMOND_WIDTH,
            color: BLINK_FEEDBACK_COLOR,
            alpha: BLINK_INNER_DIAMOND_ALPHA
          },
          {
            kind: "strokeDiamond",
            radius: radius * BLINK_INNER_DIAMOND_RADIUS_SCALE
          },
          {
            kind: "lineStyle",
            width: BLINK_TRAIL_WIDTH,
            color: BLINK_FEEDBACK_CORE_COLOR,
            alpha: BLINK_TRAIL_ALPHA
          },
          {
            kind: "lineBetween",
            x1: -facing.x * radius * BLINK_TRAIL_A_BACK_DISTANCE + perpendicular.x * radius * BLINK_TRAIL_A_SIDE_DISTANCE,
            y1: -facing.y * radius * BLINK_TRAIL_A_BACK_DISTANCE + perpendicular.y * radius * BLINK_TRAIL_A_SIDE_DISTANCE,
            x2: -facing.x * radius * BLINK_TRAIL_A_END_DISTANCE + perpendicular.x * radius * BLINK_TRAIL_A_END_SIDE_DISTANCE,
            y2: -facing.y * radius * BLINK_TRAIL_A_END_DISTANCE + perpendicular.y * radius * BLINK_TRAIL_A_END_SIDE_DISTANCE
          },
          {
            kind: "lineBetween",
            x1: -facing.x * radius * BLINK_TRAIL_B_BACK_DISTANCE - perpendicular.x * radius * BLINK_TRAIL_B_SIDE_DISTANCE,
            y1: -facing.y * radius * BLINK_TRAIL_B_BACK_DISTANCE - perpendicular.y * radius * BLINK_TRAIL_B_SIDE_DISTANCE,
            x2: -facing.x * radius * BLINK_TRAIL_B_END_DISTANCE - perpendicular.x * radius * BLINK_TRAIL_B_END_SIDE_DISTANCE,
            y2: -facing.y * radius * BLINK_TRAIL_B_END_DISTANCE - perpendicular.y * radius * BLINK_TRAIL_B_END_SIDE_DISTANCE
          },
          {
            kind: "fillStyle",
            color: BLINK_FEEDBACK_CORE_COLOR,
            alpha: release ? BLINK_RELEASE_CORE_FILL_ALPHA : BLINK_PREPARE_CORE_FILL_ALPHA
          },
          {
            kind: "fillCircle",
            x: 0,
            y: 0,
            radius: release ? BLINK_RELEASE_CORE_FILL_RADIUS : BLINK_PREPARE_CORE_FILL_RADIUS
          }
        ]
      },
      tween: {
        alpha: 0,
        scale: release ? BLINK_RELEASE_TWEEN_SCALE : BLINK_PREPARE_TWEEN_SCALE,
        durationMs: release ? BLINK_RELEASE_TWEEN_DURATION_MS : BLINK_PREPARE_TWEEN_DURATION_MS,
        ease: BLINK_TWEEN_EASE
      }
    }
  };
}

export function resolveSkillDashFeedbackVfxPlan({
  position,
  direction
}: SkillDashFeedbackVfxPlanInput): SkillDashFeedbackVfxPlan {
  const facing = normalizeDirection(direction);
  const perpendicular = perpendicularDirection(facing);
  const rotation = Math.atan2(facing.y, facing.x);

  return {
    ring: {
      graphics: {
        position,
        depth: DASH_RING_DEPTH,
        scale: DASH_RING_INITIAL_SCALE,
        commands: [
          {
            kind: "lineStyle",
            width: DASH_RING_OUTLINE_WIDTH,
            color: DASH_RING_OUTLINE_COLOR,
            alpha: DASH_RING_OUTLINE_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius: DASH_RING_INNER_RADIUS
          },
          {
            kind: "lineStyle",
            width: DASH_RING_OUTER_WIDTH,
            color: DASH_FEEDBACK_COLOR,
            alpha: DASH_RING_OUTER_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius: DASH_RING_OUTER_RADIUS
          },
          {
            kind: "lineStyle",
            width: DASH_RING_CORE_WIDTH,
            color: DASH_RING_CORE_COLOR,
            alpha: DASH_RING_CORE_ALPHA
          },
          {
            kind: "lineBetween",
            x1: facing.x * DASH_RING_ARROW_START_DISTANCE,
            y1: facing.y * DASH_RING_ARROW_START_DISTANCE,
            x2: facing.x * DASH_RING_ARROW_END_DISTANCE,
            y2: facing.y * DASH_RING_ARROW_END_DISTANCE
          },
          {
            kind: "lineBetween",
            x1: facing.x * DASH_RING_ARROW_MID_DISTANCE + perpendicular.x * DASH_RING_ARROW_HEAD_WIDTH,
            y1: facing.y * DASH_RING_ARROW_MID_DISTANCE + perpendicular.y * DASH_RING_ARROW_HEAD_WIDTH,
            x2: facing.x * DASH_RING_ARROW_HEAD_DISTANCE,
            y2: facing.y * DASH_RING_ARROW_HEAD_DISTANCE
          },
          {
            kind: "lineBetween",
            x1: facing.x * DASH_RING_ARROW_MID_DISTANCE - perpendicular.x * DASH_RING_ARROW_HEAD_WIDTH,
            y1: facing.y * DASH_RING_ARROW_MID_DISTANCE - perpendicular.y * DASH_RING_ARROW_HEAD_WIDTH,
            x2: facing.x * DASH_RING_ARROW_HEAD_DISTANCE,
            y2: facing.y * DASH_RING_ARROW_HEAD_DISTANCE
          }
        ]
      },
      tween: {
        alpha: 0,
        scale: DASH_RING_TWEEN_SCALE,
        durationMs: DASH_RING_TWEEN_DURATION_MS,
        ease: DASH_TWEEN_EASE
      }
    },
    streaks: DASH_STREAK_OFFSETS.map((offset, index) => {
      const center = index === 1;

      return {
        shape: {
          position: {
            x: position.x - facing.x * DASH_STREAK_START_BACK_DISTANCE + perpendicular.x * offset,
            y: position.y - facing.y * DASH_STREAK_START_BACK_DISTANCE + perpendicular.y * offset
          },
          width: center ? DASH_STREAK_CENTER_LENGTH : DASH_STREAK_SIDE_LENGTH,
          height: center ? DASH_STREAK_CENTER_HEIGHT : DASH_STREAK_SIDE_HEIGHT,
          color: DASH_FEEDBACK_COLOR,
          alpha: center ? DASH_STREAK_CENTER_ALPHA : DASH_STREAK_SIDE_ALPHA,
          origin: DASH_STREAK_ORIGIN,
          rotation,
          depth: DASH_STREAK_DEPTH
        },
        tween: {
          x: position.x - facing.x * DASH_STREAK_END_BACK_DISTANCE + perpendicular.x * offset,
          y: position.y - facing.y * DASH_STREAK_END_BACK_DISTANCE + perpendicular.y * offset,
          alpha: 0,
          scaleX: DASH_STREAK_TWEEN_SCALE_X,
          scaleY: DASH_STREAK_TWEEN_SCALE_Y,
          durationMs: DASH_STREAK_BASE_TWEEN_DURATION_MS + index * DASH_STREAK_TWEEN_DURATION_STEP_MS,
          ease: DASH_TWEEN_EASE
        }
      };
    })
  };
}

export function resolveSkillFreezeFeedbackRandomSamplingPlan({
  intent
}: SkillFreezeFeedbackRandomSamplingPlanInput): SkillFreezeFeedbackRandomSamplingPlan {
  const release = intent === "release";

  return {
    shardCount: release ? FREEZE_RELEASE_SHARD_COUNT : FREEZE_PREPARE_SHARD_COUNT,
    minInnerRadiusScale: FREEZE_SHARD_MIN_INNER_RADIUS_SCALE,
    maxInnerRadiusScale: FREEZE_SHARD_MAX_INNER_RADIUS_SCALE,
    minOuterRadiusScale: FREEZE_SHARD_MIN_OUTER_RADIUS_SCALE,
    maxOuterRadiusScale: FREEZE_SHARD_MAX_OUTER_RADIUS_SCALE
  };
}

export function resolveSkillFreezeFeedbackVfxPlan({
  position,
  intent,
  samples
}: SkillFreezeFeedbackVfxPlanInput): SkillFreezeFeedbackVfxPlan {
  const release = intent === "release";
  const radius = release ? FREEZE_RELEASE_FEEDBACK_RADIUS : FREEZE_PREPARE_FEEDBACK_RADIUS;
  const shardCount = samples.length;
  const shardAngleOffset = release ? FREEZE_RELEASE_SHARD_ANGLE_OFFSET : FREEZE_PREPARE_SHARD_ANGLE_OFFSET;

  return {
    marker: {
      graphics: {
        position,
        depth: FREEZE_MARKER_DEPTH,
        scale: release ? FREEZE_RELEASE_INITIAL_SCALE : FREEZE_PREPARE_INITIAL_SCALE,
        commands: [
          {
            kind: "fillStyle",
            color: FREEZE_FEEDBACK_COLOR,
            alpha: FREEZE_FILL_ALPHA
          },
          {
            kind: "fillCircle",
            x: 0,
            y: 0,
            radius: radius * FREEZE_FILL_RADIUS_SCALE
          },
          {
            kind: "lineStyle",
            width: FREEZE_OUTLINE_WIDTH,
            color: FREEZE_OUTLINE_COLOR,
            alpha: FREEZE_OUTLINE_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius
          },
          {
            kind: "lineStyle",
            width: FREEZE_OUTER_WIDTH,
            color: FREEZE_FEEDBACK_COLOR,
            alpha: FREEZE_OUTER_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius
          },
          {
            kind: "lineStyle",
            width: FREEZE_CORE_RING_WIDTH,
            color: FREEZE_FEEDBACK_CORE_COLOR,
            alpha: FREEZE_CORE_RING_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius: radius * FREEZE_CORE_RING_RADIUS_SCALE
          },
          ...samples.flatMap((sample, index) => {
            const angle = (Math.PI * 2 * index) / shardCount + shardAngleOffset;
            const inner = radius * sample.innerRadiusScale;
            const outer = radius * sample.outerRadiusScale;
            const even = index % 2 === 0;

            return [
              {
                kind: "lineStyle" as const,
                width: even ? FREEZE_SHARD_EVEN_WIDTH : FREEZE_SHARD_ODD_WIDTH,
                color: even ? FREEZE_FEEDBACK_COLOR : FREEZE_FEEDBACK_CORE_COLOR,
                alpha: FREEZE_SHARD_ALPHA
              },
              {
                kind: "lineBetween" as const,
                x1: Math.cos(angle) * inner,
                y1: Math.sin(angle) * inner,
                x2: Math.cos(angle) * outer,
                y2: Math.sin(angle) * outer
              },
              {
                kind: "lineBetween" as const,
                x1: Math.cos(angle) * outer,
                y1: Math.sin(angle) * outer,
                x2: Math.cos(angle + FREEZE_SHARD_BRANCH_ANGLE_OFFSET) * (outer - FREEZE_SHARD_BRANCH_OUTER_OFFSET),
                y2: Math.sin(angle + FREEZE_SHARD_BRANCH_ANGLE_OFFSET) * (outer - FREEZE_SHARD_BRANCH_OUTER_OFFSET)
              }
            ];
          })
        ]
      },
      tween: {
        alpha: 0,
        scale: release ? FREEZE_RELEASE_TWEEN_SCALE : FREEZE_PREPARE_TWEEN_SCALE,
        rotation: release ? FREEZE_RELEASE_TWEEN_ROTATION : FREEZE_PREPARE_TWEEN_ROTATION,
        durationMs: release ? FREEZE_RELEASE_TWEEN_DURATION_MS : FREEZE_PREPARE_TWEEN_DURATION_MS,
        ease: FREEZE_TWEEN_EASE
      }
    }
  };
}

export function resolveSkillRejectionFeedbackVfxPlan({
  position,
  radius
}: SkillRejectionFeedbackVfxPlanInput): SkillRejectionFeedbackVfxPlan {
  const size = Math.max(REJECTION_MIN_SIZE, radius * REJECTION_RADIUS_SCALE);

  return {
    marker: {
      graphics: {
        position,
        depth: REJECTION_MARKER_DEPTH,
        scale: REJECTION_INITIAL_SCALE,
        commands: [
          {
            kind: "lineStyle",
            width: REJECTION_SHADOW_WIDTH,
            color: REJECTION_SHADOW_COLOR,
            alpha: REJECTION_SHADOW_ALPHA
          },
          {
            kind: "lineBetween",
            x1: -size,
            y1: -size,
            x2: size,
            y2: size
          },
          {
            kind: "lineBetween",
            x1: -size,
            y1: size,
            x2: size,
            y2: -size
          },
          {
            kind: "lineStyle",
            width: REJECTION_CROSS_WIDTH,
            color: REJECTION_COLOR,
            alpha: REJECTION_CROSS_ALPHA
          },
          {
            kind: "lineBetween",
            x1: -size,
            y1: -size,
            x2: size,
            y2: size
          },
          {
            kind: "lineBetween",
            x1: -size,
            y1: size,
            x2: size,
            y2: -size
          },
          {
            kind: "lineStyle",
            width: REJECTION_HIGHLIGHT_WIDTH,
            color: REJECTION_HIGHLIGHT_COLOR,
            alpha: REJECTION_HIGHLIGHT_ALPHA
          },
          {
            kind: "lineBetween",
            x1: -size * 0.42,
            y1: -size * 1.18,
            x2: -size * 0.1,
            y2: -size * 0.76
          },
          {
            kind: "lineBetween",
            x1: size * 0.52,
            y1: -size * 1.08,
            x2: size * 0.16,
            y2: -size * 0.72
          },
          {
            kind: "lineBetween",
            x1: -size * 1.12,
            y1: size * 0.18,
            x2: -size * 0.72,
            y2: size * 0.06
          },
          {
            kind: "lineBetween",
            x1: size * 1.1,
            y1: size * 0.32,
            x2: size * 0.66,
            y2: size * 0.12
          },
          {
            kind: "lineStyle",
            width: REJECTION_ACCENT_WIDTH,
            color: REJECTION_COLOR,
            alpha: REJECTION_ACCENT_ALPHA
          },
          {
            kind: "strokeCircle",
            x: 0,
            y: 0,
            radius: Math.max(REJECTION_CIRCLE_MIN_RADIUS, radius * REJECTION_CIRCLE_RADIUS_SCALE)
          }
        ]
      },
      tween: {
        alpha: 0,
        scale: REJECTION_TWEEN_SCALE,
        durationMs: REJECTION_TWEEN_DURATION_MS,
        ease: REJECTION_TWEEN_EASE
      }
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
