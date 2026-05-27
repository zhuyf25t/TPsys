import type { PlayerCommand, PreparedSkill, SkillKind } from "../../../../objects/battle/types";
import { SKILL_DEFINITIONS } from "../../game/skills";

export type SkillActivationKind = "instant" | "prepared-target";
export type SkillFeedbackIntent = "prepare" | "release";
export type SkillCommandField = Extract<keyof PlayerCommand, "castDash" | "toggleBlink" | "toggleFreeze">;
export type PreparedTargetSkillKind = Exclude<PreparedSkill, null>;
export type InstantSkillKind = Exclude<SkillKind, PreparedTargetSkillKind>;

export interface PreparedTargetSkillRuntimeProfile<K extends PreparedTargetSkillKind = PreparedTargetSkillKind> {
  kind: K;
  activationKind: "prepared-target";
  commandField: SkillCommandField;
  target: {
    kind: "world-point";
    range: number;
    rangeSource: "skill-definition-range";
    indicatorRadius: number;
    feedbackRadii: Readonly<Record<SkillFeedbackIntent, number>>;
  };
}

export interface InstantSkillRuntimeProfile<K extends InstantSkillKind = InstantSkillKind> {
  kind: K;
  activationKind: "instant";
  commandField: SkillCommandField;
  feedback: {
    rejectionRadius: number;
  };
}

export type SkillRuntimeProfile = PreparedTargetSkillRuntimeProfile | InstantSkillRuntimeProfile;

const BLINK_INDICATOR_RADIUS = 11;
const BLINK_PREPARE_FEEDBACK_RADIUS = 24;
const BLINK_RELEASE_FEEDBACK_RADIUS = 28;
const DASH_REJECTION_FEEDBACK_RADIUS = 22;

export const PREPARED_TARGET_APPLY_COMMAND_ORDER: readonly PreparedTargetSkillKind[] = ["Blink", "Freeze"];
export const PREPARED_TARGET_FEEDBACK_COMMAND_PRIORITY: readonly PreparedTargetSkillKind[] = ["Freeze", "Blink"];

export const SKILL_RUNTIME_PROFILES = {
  Blink: {
    kind: "Blink",
    activationKind: "prepared-target",
    commandField: "toggleBlink",
    target: {
      kind: "world-point",
      range: SKILL_DEFINITIONS.Blink.range,
      rangeSource: "skill-definition-range",
      indicatorRadius: BLINK_INDICATOR_RADIUS,
      feedbackRadii: {
        prepare: BLINK_PREPARE_FEEDBACK_RADIUS,
        release: BLINK_RELEASE_FEEDBACK_RADIUS
      }
    }
  },
  Dash: {
    kind: "Dash",
    activationKind: "instant",
    commandField: "castDash",
    feedback: {
      rejectionRadius: DASH_REJECTION_FEEDBACK_RADIUS
    }
  },
  Freeze: {
    kind: "Freeze",
    activationKind: "prepared-target",
    commandField: "toggleFreeze",
    target: {
      kind: "world-point",
      range: SKILL_DEFINITIONS.Freeze.range,
      rangeSource: "skill-definition-range",
      indicatorRadius: SKILL_DEFINITIONS.Freeze.radius,
      feedbackRadii: {
        prepare: SKILL_DEFINITIONS.Freeze.radius * 0.2,
        release: SKILL_DEFINITIONS.Freeze.radius
      }
    }
  }
} as const satisfies {
  readonly Blink: PreparedTargetSkillRuntimeProfile<"Blink">;
  readonly Dash: InstantSkillRuntimeProfile<"Dash">;
  readonly Freeze: PreparedTargetSkillRuntimeProfile<"Freeze">;
};

/** 中文名：获取技能runtimeprofile（getSkillRuntimeProfile）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getSkillRuntimeProfile(kind: SkillKind): SkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

/** 中文名：获取prepared目标技能runtimeprofile（getPreparedTargetSkillRuntimeProfile）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getPreparedTargetSkillRuntimeProfile(
  kind: PreparedTargetSkillKind
): PreparedTargetSkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

/** 中文名：获取instant技能runtimeprofile（getInstantSkillRuntimeProfile）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getInstantSkillRuntimeProfile(kind: InstantSkillKind): InstantSkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

/** 中文名：判断是否prepared目标技能kind（isPreparedTargetSkillKind）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isPreparedTargetSkillKind(kind: SkillKind | PreparedSkill): kind is PreparedTargetSkillKind {
  return kind === "Blink" || kind === "Freeze";
}

/** 中文名：判断是否技能命令pressed（isSkillCommandPressed）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isSkillCommandPressed(command: PlayerCommand, kind: SkillKind): boolean {
  return command[getSkillRuntimeProfile(kind).commandField];
}

/** 中文名：解析prepared目标技能命令（resolvePreparedTargetSkillCommand）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolvePreparedTargetSkillCommand(
  command: PlayerCommand,
  priority: readonly PreparedTargetSkillKind[]
): PreparedTargetSkillKind | null {
  for (const kind of priority) {
    if (isSkillCommandPressed(command, kind)) {
      return kind;
    }
  }

  return null;
}

/** 中文名：获取prepared目标技能feedbackradius（getPreparedTargetSkillFeedbackRadius）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getPreparedTargetSkillFeedbackRadius(
  kind: PreparedTargetSkillKind,
  intent: SkillFeedbackIntent
): number {
  return getPreparedTargetSkillRuntimeProfile(kind).target.feedbackRadii[intent];
}
