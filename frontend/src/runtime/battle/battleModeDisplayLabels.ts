import type { BattleModeIdDto } from "../../objects/battle/contracts/apiMessages";

export const NORMAL_BATTLE_MODE_LABEL = "森林模式";

export function battleModeDisplayLabel(modeId: BattleModeIdDto, fallbackLabel: string): string {
  return modeId === "normal" ? NORMAL_BATTLE_MODE_LABEL : fallbackLabel;
}
