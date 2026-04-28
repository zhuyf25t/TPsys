import type { Hero, SkillKind, SkillState } from "../domain/types";

export interface SkillDefinition {
  cooldownMs: number;
  range: number;
  radius: number;
  durationMs: number;
  healAmount: number;
  distance: number;
}

export const SKILL_DEFINITIONS: Record<SkillKind, SkillDefinition> = {
  Blink: {
    cooldownMs: 2200,
    range: 250,
    radius: 0,
    durationMs: 0,
    healAmount: 0,
    distance: 0
  },
  Dash: {
    cooldownMs: 2600,
    range: 0,
    radius: 0,
    durationMs: 0,
    healAmount: 0,
    distance: 180
  },
  Freeze: {
    cooldownMs: 12000,
    range: 520,
    radius: 150,
    durationMs: 10000,
    healAmount: 0,
    distance: 0
  }
};

export function createDefaultSkills(): SkillState[] {
  return [
    { kind: "Blink", cooldownMs: 0, activeMs: 0 },
    { kind: "Dash", cooldownMs: 0, activeMs: 0 },
    { kind: "Freeze", cooldownMs: 0, activeMs: 0 }
  ];
}

export function getSkillState(hero: Hero, kind: SkillKind): SkillState {
  const skill = hero.skills.find((item) => item.kind === kind);

  if (!skill) {
    throw new Error(`Missing skill state for ${kind}`);
  }

  return skill;
}
