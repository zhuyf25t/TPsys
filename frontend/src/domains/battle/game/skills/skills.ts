import type { Hero, SkillKind, SkillState } from "../../objects/types";
import { SKILL_DEFINITIONS, type SkillDefinition } from "../assets/battleContentCatalog";

export { SKILL_DEFINITIONS, type SkillDefinition };

/** 中文名：创建defaultskills（createDefaultSkills）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createDefaultSkills(): SkillState[] {
  return [
    { kind: "Blink", cooldownMs: 0, activeMs: 0 },
    { kind: "Dash", cooldownMs: 0, activeMs: 0 },
    { kind: "Freeze", cooldownMs: 0, activeMs: 0 }
  ];
}

/** 中文名：获取技能状态（getSkillState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getSkillState(hero: Hero, kind: SkillKind): SkillState {
  const skill = hero.skills.find((item) => item.kind === kind);

  if (!skill) {
    throw new Error(`Missing skill state for ${kind}`);
  }

  return skill;
}
