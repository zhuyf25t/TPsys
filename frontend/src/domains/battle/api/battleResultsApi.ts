import { buildApiUrl, normalizeApiBase } from "../../../shared/api/apiUrl";
import { backfillLocalBattleTruthToBackend } from "../runtime/local/state/battleResultSync";

export interface BackendBattleResultRecord {
  resultId: string;
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

type RemoteBattleResultRecordDto = {
  [Field in keyof BackendBattleResultRecord]?: unknown;
};

type LocalLegacyBattleResultRecordDto = {
  id?: unknown;
  resultId?: unknown;
  battleId?: unknown;
  handle?: unknown;
  playerName?: unknown;
  displayName?: unknown;
  finishedAt?: unknown;
  finishedAtLabel?: unknown;
  durationMs?: unknown;
  score?: unknown;
  placement?: unknown;
  aliveAtEnd?: unknown;
  ratingBefore?: unknown;
  ratingDelta?: unknown;
  ratingAfter?: unknown;
  resultLabel?: unknown;
  modeLabel?: unknown;
  mapLabel?: unknown;
  highlightLine?: unknown;
  playersLine?: unknown;
  timelineHint?: unknown;
  currentLoadout?: unknown;
};

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const BATTLE_RESULTS_TIMEOUT_MS = 5_000;
const LOCAL_BATTLE_TRUTH_STORAGE_KEY = "slay-demo.truthful-battle-data.v2";

export async function loadBattleResults(options?: {
  handle?: string;
  battleId?: string;
  limit?: number;
  includeLocalFallback?: boolean;
}): Promise<BackendBattleResultRecord[] | null> {
  if (typeof window === "undefined") {
    return null;
  }

  const localRecords = options?.includeLocalFallback === false ? [] : loadLocalBattleResults(options);
  if (!BATTLE_API_BASE) {
    return filterBattleResultsByBattleId(localRecords, options?.battleId);
  }

  await backfillLocalBattleTruthToBackend();
  const remoteRecords = await loadRemoteBattleResults(options);
  if (!remoteRecords) {
    return filterBattleResultsByBattleId(localRecords, options?.battleId);
  }

  return filterBattleResultsByBattleId(mergeBattleResults(localRecords, remoteRecords, options?.limit), options?.battleId);
}

export async function loadBattleResultByBattleId(
  battleId: string,
  handle?: string | null
): Promise<BackendBattleResultRecord | null> {
  const normalizedBattleId = normalizeBattleResultId(battleId);
  if (!normalizedBattleId) {
    return null;
  }

  const records = await loadRemoteBattleResults({
    battleId: normalizedBattleId,
    limit: 50
  });
  const battleRecords = records?.filter((record) => normalizeBattleResultId(record.battleId) === normalizedBattleId) ?? [];
  const normalizedHandle = normalizeHandle(handle ?? "");
  return (
    (normalizedHandle
      ? battleRecords.find((record) => normalizeHandle(record.handle) === normalizedHandle)
      : undefined) ??
    battleRecords.find((record) => record.placement === 1) ??
    battleRecords[0] ??
    null
  );
}

async function loadRemoteBattleResults(options?: {
  handle?: string;
  battleId?: string;
  limit?: number;
}): Promise<BackendBattleResultRecord[] | null> {
  if (typeof window === "undefined" || !BATTLE_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), BATTLE_RESULTS_TIMEOUT_MS);

  try {
    const url = buildApiUrl(BATTLE_API_BASE, "/battleresultsapi", {
      handle: options?.handle,
      battleId: options?.battleId,
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

    const payload = (await response.json().catch(() => null)) as BackendBattleResultsResponse | null;
    const rawRecords = Array.isArray(payload?.results) ? payload.results : [];

    return rawRecords
      .map((record) => normalizeRemoteBattleResultRecord(record))
      .filter((record): record is BackendBattleResultRecord => record !== null);
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

export function loadLocalBattleResults(options?: {
  handle?: string;
  limit?: number;
}): BackendBattleResultRecord[] {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(LOCAL_BATTLE_TRUTH_STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw) as { records?: unknown };
    const records = Array.isArray(parsed.records)
      ? parsed.records
          .map((record) => normalizeLocalBattleResultRecord(record))
          .filter((record): record is BackendBattleResultRecord => record !== null)
      : [];

    const normalizedHandle = normalizeHandle(options?.handle ?? "");
    const filtered = normalizedHandle
      ? records.filter((record) => normalizeHandle(record.handle) === normalizedHandle)
      : records;

    return filtered
      .sort(compareBattleResultByRecentness)
      .slice(0, normalizeLimit(options?.limit));
  } catch {
    return [];
  }
}

function normalizeRemoteBattleResultRecord(value: unknown): BackendBattleResultRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as RemoteBattleResultRecordDto;
  if (!hasRemoteBattleResultFields(record)) {
    return null;
  }

  if (!String(record.battleId).trim() || !String(record.handle).trim() || !String(record.displayName).trim()) {
    return null;
  }

  return {
    resultId: normalizeResultId(record.resultId, String(record.battleId), String(record.handle)),
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

function normalizeLocalBattleResultRecord(value: unknown): BackendBattleResultRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as LocalLegacyBattleResultRecordDto;
  const battleId = String(record.id ?? record.battleId ?? "").trim();
  const handle = String(record.handle ?? record.playerName ?? "").trim();
  const displayName = String(record.playerName ?? record.displayName ?? handle).trim();
  if (!battleId || !handle || !displayName) {
    return null;
  }

  const finishedAt = coerceNumber(record.finishedAt);

  return normalizeRemoteBattleResultRecord({
    resultId: record.resultId,
    battleId,
    handle,
    displayName,
    finishedAt,
    finishedAtLabel: String(record.finishedAtLabel ?? formatLocalFinishedAt(finishedAt)),
    durationMs: coerceNumber(record.durationMs),
    score: coerceNumber(record.score),
    placement: coerceNullableNumber(record.placement),
    aliveAtEnd: Boolean(record.aliveAtEnd),
    ratingBefore: coerceNumber(record.ratingBefore),
    ratingDelta: coerceNumber(record.ratingDelta),
    ratingAfter: coerceNumber(record.ratingAfter),
    resultLabel: String(record.resultLabel ?? "本地战报"),
    modeLabel: String(record.modeLabel ?? "本地对局"),
    mapLabel: String(record.mapLabel ?? ""),
    highlightLine: String(record.highlightLine ?? ""),
    playersLine: String(record.playersLine ?? ""),
    timelineHint: String(record.timelineHint ?? ""),
    currentLoadout:
      record.currentLoadout === null || typeof record.currentLoadout === "undefined"
        ? null
        : String(record.currentLoadout)
  });
}

function hasRemoteBattleResultFields(record: RemoteBattleResultRecordDto): record is Required<RemoteBattleResultRecordDto> {
  return REMOTE_BATTLE_RESULT_FIELDS.every((field) => Object.prototype.hasOwnProperty.call(record, field));
}

const REMOTE_BATTLE_RESULT_FIELDS = [
  "battleId",
  "handle",
  "displayName",
  "finishedAt",
  "finishedAtLabel",
  "durationMs",
  "score",
  "placement",
  "aliveAtEnd",
  "ratingBefore",
  "ratingDelta",
  "ratingAfter",
  "resultLabel",
  "modeLabel",
  "mapLabel",
  "highlightLine",
  "playersLine",
  "timelineHint",
  "currentLoadout"
] satisfies (keyof BackendBattleResultRecord)[];

function compareBattleResultByRecentness(
  left: BackendBattleResultRecord,
  right: BackendBattleResultRecord
): number {
  if (right.finishedAt !== left.finishedAt) {
    return right.finishedAt - left.finishedAt;
  }

  return left.resultId.localeCompare(right.resultId);
}

function mergeBattleResults(
  localRecords: BackendBattleResultRecord[],
  remoteRecords: BackendBattleResultRecord[],
  limit: number | undefined
): BackendBattleResultRecord[] {
  const merged = new Map<string, BackendBattleResultRecord>();

  localRecords.forEach((record) => {
    const key = normalizeBattleResultId(record.resultId);
    if (key) {
      merged.set(key, record);
    }
  });

  remoteRecords.forEach((record) => {
    const key = normalizeBattleResultId(record.resultId);
    if (key) {
      merged.set(key, record);
    }
  });

  return Array.from(merged.values())
    .sort(compareBattleResultByRecentness)
    .slice(0, normalizeLimit(limit));
}

function filterBattleResultsByBattleId(
  records: BackendBattleResultRecord[],
  battleId: string | undefined
): BackendBattleResultRecord[] {
  const normalizedBattleId = normalizeBattleResultId(battleId ?? "");
  if (!normalizedBattleId) {
    return records;
  }

  return records.filter((record) => normalizeBattleResultId(record.battleId) === normalizedBattleId);
}

function normalizeLimit(limit: number | undefined): number {
  if (typeof limit === "undefined") {
    return Number.POSITIVE_INFINITY;
  }

  const next = Math.trunc(limit);
  return Number.isFinite(next) && next >= 0 ? next : Number.POSITIVE_INFINITY;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function normalizeBattleResultId(battleId: string): string {
  return battleId.trim();
}

function normalizeResultId(resultId: unknown, battleId: string, handle: string): string {
  const explicit = String(resultId ?? "").trim();
  if (explicit) {
    return explicit;
  }

  return `${battleId.trim()}:${normalizeHandle(handle)}`;
}

function formatLocalFinishedAt(finishedAt: number): string {
  if (!Number.isFinite(finishedAt) || finishedAt <= 0) {
    return "";
  }

  return new Date(finishedAt).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
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
