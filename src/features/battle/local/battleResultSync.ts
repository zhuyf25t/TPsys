import { buildApiUrl, normalizeApiBase } from "../../api/apiUrl";

export interface BattleResultSyncPayload {
  battleId: string;
  handle: string;
  displayName: string;
  finishedAt: number;
  finishedAtLabel: string;
  durationMs: number;
  score: number;
  placement: number | null;
  aliveAtEnd: boolean;
  ratingBefore: number;
  ratingDelta: number;
  ratingAfter: number;
  resultLabel: string;
  modeLabel: string;
  mapLabel: string;
  highlightLine: string;
  playersLine: string;
  timelineHint: string;
  currentLoadout: string | null;
}

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");

export function syncBattleResultToBackend(payload: BattleResultSyncPayload): void {
  if (typeof window === "undefined") {
    return;
  }

  void fetch(buildApiUrl(BATTLE_API_BASE, "/battle/results"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  }).catch(() => undefined);
}
