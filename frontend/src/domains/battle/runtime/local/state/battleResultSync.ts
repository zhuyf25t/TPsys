import { buildApiUrl, normalizeApiBase } from "../../../../../shared/api/apiUrl";

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

export async function syncBattleResultToBackend(payload: BattleResultSyncPayload): Promise<boolean> {
  if (typeof window === "undefined") {
    return false;
  }

  try {
    const response = await fetch(buildApiUrl(BATTLE_API_BASE, "/battleresultsapi"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      keepalive: true
    });

    return response.ok;
  } catch {
    return false;
  }
}

export async function backfillLocalBattleTruthToBackend(): Promise<void> {
  if (typeof window === "undefined") {
    return;
  }

  try {
    const { backfillLocalBattleTruthToBackend: runBattleTruthBackfill } = await import("./battleTruthStore");
    await runBattleTruthBackfill();
  } catch {
    // Backfill is best effort; the live battle result flow already handles direct settlement.
  }
}
