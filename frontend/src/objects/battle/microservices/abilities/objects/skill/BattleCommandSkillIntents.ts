import type { SkillKind } from "./SkillKind";

export interface BattleCommandSkillIntents {
  values: SkillKind[];
}

export const emptyBattleCommandSkillIntents: BattleCommandSkillIntents = {
  values: []
};

export function battleCommandSkillIntentsFromLegacyFlags(input: {
  castDash: boolean;
  castBlink: boolean;
  castFreeze: boolean;
  castCritical: boolean;
}): BattleCommandSkillIntents {
  return {
    values: [
      input.castBlink ? "Blink" : null,
      input.castDash ? "Dash" : null,
      input.castFreeze ? "Freeze" : null,
      input.castCritical ? "Critical" : null
    ].filter((value): value is SkillKind => value !== null)
  };
}
