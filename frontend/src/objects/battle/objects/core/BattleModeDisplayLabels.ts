import type { BattleModeId } from "./BattleCoreScalars";

export const NORMAL_BATTLE_MODE_LABEL = "森林模式";
export const ZOMBIE_BATTLE_MODE_LABEL = "丧尸模式";

export function battleModeDisplayLabel(modeId: BattleModeId, fallbackLabel: string): string {
  if (modeId === "winter") {
    return ZOMBIE_BATTLE_MODE_LABEL;
  }

  return modeId === "normal" ? NORMAL_BATTLE_MODE_LABEL : fallbackLabel;
}
