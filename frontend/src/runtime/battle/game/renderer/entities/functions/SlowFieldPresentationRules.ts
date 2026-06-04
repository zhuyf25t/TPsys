import type { BattleSlowFieldState as SlowField } from "../../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type {
  ResolveSlowFieldViewPlanInput,
  SlowFieldViewCreationPlan,
  SlowFieldViewReleasePlan,
  SlowFieldViewVisualPlan
} from "../objects/SlowFieldViewObjects";

const SLOW_FIELD_FILL_TINT = 0x9beeff;
const SLOW_FIELD_RIM_TINT = 0xb9f7ff;
const SLOW_FIELD_FILL_ALPHA = 0.12;
const SLOW_FIELD_RIM_ALPHA = 0.58;
const SLOW_FIELD_RIM_WIDTH = 3;
const SLOW_FIELD_FILL_DEPTH = 21;
const SLOW_FIELD_RIM_DEPTH = 22;

export function resolveSlowFieldAlpha(field: SlowField): number {
  return clamp(field.ttlMs / Math.max(1, field.durationMs), 0, 1);
}

export function resolveSlowFieldViewCreationPlan({
  field
}: ResolveSlowFieldViewPlanInput): SlowFieldViewCreationPlan {
  return {
    fill: {
      position: field.position,
      radius: field.radius,
      fillColor: SLOW_FIELD_FILL_TINT,
      fillAlpha: SLOW_FIELD_FILL_ALPHA,
      depth: SLOW_FIELD_FILL_DEPTH
    },
    rim: {
      position: field.position,
      radius: field.radius,
      fillColor: SLOW_FIELD_RIM_TINT,
      fillAlpha: 0,
      depth: SLOW_FIELD_RIM_DEPTH,
      stroke: {
        width: SLOW_FIELD_RIM_WIDTH,
        color: SLOW_FIELD_RIM_TINT,
        alpha: SLOW_FIELD_RIM_ALPHA
      }
    }
  };
}

export function resolveSlowFieldViewVisualPlan({
  field
}: ResolveSlowFieldViewPlanInput): SlowFieldViewVisualPlan {
  const alpha = resolveSlowFieldAlpha(field);

  return {
    fill: {
      position: field.position,
      radius: field.radius,
      fill: {
        color: SLOW_FIELD_FILL_TINT,
        alpha: SLOW_FIELD_FILL_ALPHA * alpha
      }
    },
    rim: {
      position: field.position,
      radius: field.radius,
      stroke: {
        width: SLOW_FIELD_RIM_WIDTH,
        color: SLOW_FIELD_RIM_TINT,
        alpha: SLOW_FIELD_RIM_ALPHA * alpha
      }
    }
  };
}

export function resolveSlowFieldViewReleasePlan(): SlowFieldViewReleasePlan {
  return {
    fill: { destroy: true },
    rim: { destroy: true }
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
