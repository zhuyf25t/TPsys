export const BATTLE_ARENA_PLAYER_CAPACITY = 6;
export const BATTLE_MATCH_DURATION_MS = 5 * 60 * 1000;
export const BATTLE_MATCH_DURATION_LABEL = "5 分钟";
export const BATTLE_MATCHMAKING_DURATION_MS = 5_000;
export const BATTLE_LIFE_MODE_LABEL = "单命淘汰";
export const BATTLE_RESULT_POLICY_LABEL = "按淘汰顺序结算";
export const BATTLE_VISITOR_HANDLE = "Visitor";

export function isBattleVisitorHandle(handle: string | null | undefined): boolean {
  return (handle ?? "").trim().toLowerCase() === BATTLE_VISITOR_HANDLE.toLowerCase();
}
