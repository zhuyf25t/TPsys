import type { BattleModeId } from "./BattleCoreScalars";

export const BATTLE_ARENA_PLAYER_CAPACITY = 6;
export const BATTLE_EXTENDED_ARENA_PLAYER_CAPACITY = 12;
export const BATTLE_MATCH_DURATION_MS = 5 * 60 * 1000;
export const BATTLE_MATCH_DURATION_LABEL = "5 分钟";
export const BATTLE_MATCHMAKING_DURATION_MS = 5_000;
export const BATTLE_LIFE_MODE_LABEL = "单命淘汰";
export const BATTLE_RESULT_POLICY_LABEL = "按淘汰顺序结算";
export const BATTLE_VISITOR_HANDLE = "Visitor";

/** 中文名：判断是否战斗visitor玩家名（isBattleVisitorHandle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function battleArenaPlayerCapacityForMode(modeId: BattleModeId | null | undefined): number {
  return modeId === "autumn" || modeId === "winter"
    ? BATTLE_EXTENDED_ARENA_PLAYER_CAPACITY
    : BATTLE_ARENA_PLAYER_CAPACITY;
}

export function isBattleVisitorHandle(handle: string | null | undefined): boolean {
  return (handle ?? "").trim().toLowerCase() === BATTLE_VISITOR_HANDLE.toLowerCase();
}
