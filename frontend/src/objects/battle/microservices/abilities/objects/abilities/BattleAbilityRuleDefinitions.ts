import type { SkillKind } from "../skill/SkillKind";

export type SkillActivationKind = "instant" | "prepared-target";
export type SkillEffectType = "dash" | "teleport" | "slow-field" | "damage-boost";

interface BattleSkillRuleDefinitionBase {
  skillKind: SkillKind;
  activationKind: SkillActivationKind;
  effectType: SkillEffectType;
  cooldownMs: number;
  activeMs: number;
}

export interface BlinkSkillDefinition extends BattleSkillRuleDefinitionBase {
  skillKind: "Blink";
  activationKind: "prepared-target";
  effectType: "teleport";
  range: number;
}

export interface DashSkillDefinition extends BattleSkillRuleDefinitionBase {
  skillKind: "Dash";
  activationKind: "instant";
  effectType: "dash";
  distance: number;
}

export interface FreezeSkillDefinition extends BattleSkillRuleDefinitionBase {
  skillKind: "Freeze";
  activationKind: "prepared-target";
  effectType: "slow-field";
  range: number;
  radius: number;
  durationMs: number;
  speedMultiplier: number;
}

export interface CriticalSkillDefinition extends BattleSkillRuleDefinitionBase {
  skillKind: "Critical";
  activationKind: "instant";
  effectType: "damage-boost";
  damageMultiplier: number;
  durationMs: number;
}

export type BattleSkillRuleDefinition =
  | BlinkSkillDefinition
  | DashSkillDefinition
  | FreezeSkillDefinition
  | CriticalSkillDefinition;
export type SkillDefinition = BattleSkillRuleDefinition;

export const SKILL_DEFINITIONS = {
  Blink: {
    skillKind: "Blink",
    activationKind: "prepared-target",
    effectType: "teleport",
    cooldownMs: 7000,
    activeMs: 240,
    range: 250
  },
  Dash: {
    skillKind: "Dash",
    activationKind: "instant",
    effectType: "dash",
    cooldownMs: 5000,
    activeMs: 180,
    distance: 180
  },
  Freeze: {
    skillKind: "Freeze",
    activationKind: "prepared-target",
    effectType: "slow-field",
    cooldownMs: 10000,
    activeMs: 10000,
    range: 520,
    radius: 150,
    durationMs: 10000,
    speedMultiplier: 0.5
  },
  Critical: {
    skillKind: "Critical",
    activationKind: "instant",
    effectType: "damage-boost",
    cooldownMs: 7000,
    activeMs: 6000,
    damageMultiplier: 1.5,
    durationMs: 6000
  }
} as const satisfies Readonly<Record<SkillKind, Readonly<BattleSkillRuleDefinition>>>;
