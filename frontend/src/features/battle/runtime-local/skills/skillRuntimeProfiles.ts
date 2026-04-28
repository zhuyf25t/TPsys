import type { PlayerCommand, PreparedSkill, SkillKind } from "../../../../domain/types";
import { SKILL_DEFINITIONS } from "../../../../game/skills";

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

export function getSkillRuntimeProfile(kind: SkillKind): SkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

export function getPreparedTargetSkillRuntimeProfile(
  kind: PreparedTargetSkillKind
): PreparedTargetSkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

export function getInstantSkillRuntimeProfile(kind: InstantSkillKind): InstantSkillRuntimeProfile {
  return SKILL_RUNTIME_PROFILES[kind];
}

export function isPreparedTargetSkillKind(kind: SkillKind | PreparedSkill): kind is PreparedTargetSkillKind {
  return kind === "Blink" || kind === "Freeze";
}

export function isSkillCommandPressed(command: PlayerCommand, kind: SkillKind): boolean {
  return command[getSkillRuntimeProfile(kind).commandField];
}

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

export function getPreparedTargetSkillFeedbackRadius(
  kind: PreparedTargetSkillKind,
  intent: SkillFeedbackIntent
): number {
  return getPreparedTargetSkillRuntimeProfile(kind).target.feedbackRadii[intent];
}
