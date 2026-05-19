import type { LoadoutSkillId, SkillSlotKey } from "../../../api/loadoutGateway";

export type SkillBindingAction = "Blink" | "Dash" | "Freeze";

export type SkillSlotPressMap = Record<SkillSlotKey, boolean>;

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
