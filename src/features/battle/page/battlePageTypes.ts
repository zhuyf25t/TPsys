import type { GameSnapshot } from "../../../domain/types";
import { BATTLE_MATCH_DURATION_MS } from "../local/battleLocalGateway";
import type { ReplayFrame } from "../../replay/replayTypes";

export const MATCHMAKING_DURATION_MS = 10_000;

export type MatchPhase = "matching" | "playing" | "settled";
export type BattleDrawerId = "replay" | "discussion" | "rating" | "mails" | "social";

export interface ActiveBattleSession {
  version: 1;
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

export function formatMatchmakingTime(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function getBattleAliveHeroCount(snapshot: GameSnapshot): number {
  return snapshot.heroes.filter((hero) => hero.alive && hero.lifeState === "alive" && hero.hp > 0).length;
}

export function isBattleComplete(snapshot: GameSnapshot | null): snapshot is GameSnapshot {
  if (!snapshot) {
    return false;
  }

  return getBattleAliveHeroCount(snapshot) <= 1 || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS;
}
