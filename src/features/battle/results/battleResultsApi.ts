import { buildApiUrl, normalizeApiBase } from "../../api/apiUrl";

export interface BackendBattleResultRecord {
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

interface BackendBattleResultsResponse {
  results?: unknown;
}

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const BATTLE_RESULTS_TIMEOUT_MS = 5_000;

export async function loadBattleResults(options?: {
  handle?: string;
  limit?: number;
}): Promise<BackendBattleResultRecord[] | null> {
  if (typeof window === "undefined" || !BATTLE_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), BATTLE_RESULTS_TIMEOUT_MS);

  try {
    const url = buildApiUrl(BATTLE_API_BASE, "/battle/results", {
      handle: options?.handle,
      limit: options?.limit
    });

    const response = await fetch(url, {
      method: "GET",
      cache: "no-store",
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    const payload = (await response.json().catch(() => null)) as BackendBattleResultsResponse | BackendBattleResultRecord[] | null;
    const rawRecords = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.results)
        ? payload.results
        : [];

    const records = rawRecords
      .map((record) => normalizeBattleResultRecord(record))
      .filter((record): record is BackendBattleResultRecord => record !== null);

    return records;
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeBattleResultRecord(value: unknown): BackendBattleResultRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as Partial<BackendBattleResultRecord>;
  if (!record.battleId || !record.handle || !record.displayName) {
    return null;
  }

  return {
    battleId: String(record.battleId),
    handle: String(record.handle),
    displayName: String(record.displayName),
    finishedAt: coerceNumber(record.finishedAt),
    finishedAtLabel: String(record.finishedAtLabel ?? ""),
    durationMs: coerceNumber(record.durationMs),
    score: coerceNumber(record.score),
    placement: coerceNullableNumber(record.placement),
    aliveAtEnd: Boolean(record.aliveAtEnd),
    ratingBefore: coerceNumber(record.ratingBefore),
    ratingDelta: coerceNumber(record.ratingDelta),
    ratingAfter: coerceNumber(record.ratingAfter),
    resultLabel: String(record.resultLabel ?? ""),
    modeLabel: String(record.modeLabel ?? ""),
    mapLabel: String(record.mapLabel ?? ""),
    highlightLine: String(record.highlightLine ?? ""),
    playersLine: String(record.playersLine ?? ""),
    timelineHint: String(record.timelineHint ?? ""),
    currentLoadout:
      record.currentLoadout === null || typeof record.currentLoadout === "undefined"
        ? null
        : String(record.currentLoadout)
  };
}

function coerceNumber(value: unknown): number {
  const next = Number(value);
  return Number.isFinite(next) ? next : 0;
}

function coerceNullableNumber(value: unknown): number | null {
  if (value === null || typeof value === "undefined") {
    return null;
  }

  const next = Number(value);
  return Number.isFinite(next) ? next : null;
}
