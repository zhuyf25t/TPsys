import type {
  BattleHeroViewState as Hero,
  BattlePreparedSkill
} from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleStateSkillResponseDto as SkillState } from "../../../../../objects/battle/microservices/session/api/state/BattleStatePlayerResponseApiTypes";
import type { SkillKind } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";
import {
  SKILL_DEFINITIONS,
  type SkillDefinition
} from "../../../../../objects/battle/microservices/abilities/objects/abilities/BattleAbilityRuleDefinitions";

export { SKILL_DEFINITIONS, type SkillDefinition };

export function createDefaultSkills(): SkillState[] {
  return [
    { kind: "Blink", cooldownMs: 0, activeMs: 0 },
    { kind: "Dash", cooldownMs: 0, activeMs: 0 },
    { kind: "Freeze", cooldownMs: 0, activeMs: 0 },
    { kind: "Critical", cooldownMs: 0, activeMs: 0 }
  ];
}

export function getSkillState(hero: Hero, kind: SkillKind): SkillState {
  const skill = hero.skills.find((item) => item.kind === kind);

  if (!skill) {
    throw new Error(`Missing skill state for ${kind}`);
  }

  return skill;
}

export function isBattleSkillReady(skill: SkillState): boolean {
  return skill.cooldownMs <= 0;
}

export function activateBattleSkillState(
  skill: SkillState,
  definition: Pick<SkillDefinition, "cooldownMs" | "activeMs">
): SkillState {
  return {
    ...skill,
    cooldownMs: definition.cooldownMs,
    activeMs: definition.activeMs
  };
}

export function updateBattleSkillState(
  skills: readonly SkillState[],
  kind: SkillKind,
  nextSkill: SkillState
): SkillState[] {
  return skills.map((skill) => (skill.kind === kind ? nextSkill : skill));
}

export function resolveBattlePreparedSkillToggle(
  preparedSkill: BattlePreparedSkill,
  kind: Exclude<BattlePreparedSkill, null>
): BattlePreparedSkill {
  return preparedSkill === kind ? null : kind;
}

export function advanceBattleSkillTimer(skill: SkillState, deltaMs: number): SkillState {
  const safeDeltaMs = Math.max(0, deltaMs);
  return {
    ...skill,
    cooldownMs: Math.max(0, skill.cooldownMs - safeDeltaMs),
    activeMs: Math.max(0, skill.activeMs - safeDeltaMs)
  };
}
