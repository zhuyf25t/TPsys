import type { GameSnapshot } from "../../objects/battle/types";
import type { ReplayFrame } from "../../objects/replay/replayTypes";
import { BATTLE_MATCHMAKING_DURATION_MS } from "../../objects/battle/battleRules";

export const MATCHMAKING_DURATION_MS = BATTLE_MATCHMAKING_DURATION_MS;

export type MatchPhase = "matching" | "playing" | "settled";
export type BattleDrawerId = "replay" | "discussion" | "rating" | "mails" | "social";

export interface ActiveBattleSessionOwner {
  handle: string;
  sessionToken: string | null;
}

export interface ActiveBattleSession {
  version: 1;
  owner: ActiveBattleSessionOwner;
  sessionEpoch?: string;
  battleId: string;
  mapId?: string;
  sharedAuthoritativeRuntime?: boolean;
  localAuthoritativePlayerId?: string;
  localAuthoritativeTicketId?: string;
  savedAt: number;
  snapshot: GameSnapshot;
  replayFrames: ReplayFrame[];
  lastReplaySampleElapsed: number | null;
}

export const QUICK_LEFT: Array<{ id: BattleDrawerId; label: string; iconKey: "replay" | "discussion" | "ranking" }> = [
  { id: "replay", label: "回放", iconKey: "replay" },
  { id: "discussion", label: "论坛", iconKey: "discussion" },
  { id: "rating", label: "排行", iconKey: "ranking" }
];

export const QUICK_RIGHT: Array<{ id: BattleDrawerId; label: string; iconKey: "mails" | "social" }> = [
  { id: "mails", label: "邮件", iconKey: "mails" },
  { id: "social", label: "好友", iconKey: "social" }
];

/** 中文名：格式化matchmaking时间（formatMatchmakingTime）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function formatMatchmakingTime(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
