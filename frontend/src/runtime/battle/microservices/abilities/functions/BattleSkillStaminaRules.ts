import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { SkillKind } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";

export interface BattleSkillStaminaSpendResult {
  ok: boolean;
  requiredStamina: number;
  message: string | null;
}

const SKILL_STAMINA_COST_RATIOS: Readonly<Record<SkillKind, number>> = {
  Blink: 0,
  Dash: 0.2,
  Freeze: 0.2,
  Critical: 0.4
};

const SKILL_STAMINA_LABELS: Readonly<Record<SkillKind, string>> = {
  Blink: "闪现",
  Dash: "冲刺",
  Freeze: "冰冻",
  Critical: "暴击"
};

export function consumeBattleSkillStamina(hero: Hero, skillKind: SkillKind): BattleSkillStaminaSpendResult {
  const requiredStamina = getBattleSkillRequiredStamina(hero, skillKind);
  if (hero.stamina + 0.0001 < requiredStamina) {
    return {
      ok: false,
      requiredStamina,
      message: `${SKILL_STAMINA_LABELS[skillKind]}技能需要体力${Math.ceil(requiredStamina)}，体力不够无法使用`
    };
  }

  hero.stamina = Math.max(0, hero.stamina - requiredStamina);
  return {
    ok: true,
    requiredStamina,
    message: null
  };
}

export function getBattleSkillRequiredStamina(hero: Hero, skillKind: SkillKind): number {
  return Math.max(0, hero.maxStamina * SKILL_STAMINA_COST_RATIOS[skillKind]);
}
