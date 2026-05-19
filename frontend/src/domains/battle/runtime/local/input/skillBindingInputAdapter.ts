import type { LoadoutSkillId, SkillSlotKey } from "../../../api/loadoutGateway";

export type SkillBindingAction = "Blink" | "Dash" | "Freeze";

export type SkillSlotPressMap = Record<SkillSlotKey, boolean>;

/** 中文名：读取技能bindingpresses（readSkillBindingPresses）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function readSkillBindingPresses(
  bindings: Record<SkillSlotKey, LoadoutSkillId>,
  slotPresses: SkillSlotPressMap
): Record<SkillBindingAction, boolean> {
  const pressed: Record<SkillBindingAction, boolean> = {
    Blink: false,
    Dash: false,
    Freeze: false
  };

  for (const key of ["Q", "E", "R"] as SkillSlotKey[]) {
    if (!slotPresses[key]) {
      continue;
    }

    pressed[bindings[key]] = true;
  }

  return pressed;
}
