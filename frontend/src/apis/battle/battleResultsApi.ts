import {
  postBattleResultListAPIMessage,
  type BattleResultListAPIMessageRequest
} from "./battleApiMessageClient";
import { backfillLocalBattleTruthToBackend } from "../../runtime/battle/local/state/battleTruthStore";
import type {
  BattleResultListResponseDto,
  BattleResultRecordResponseDto
} from "../../objects/battle/contracts/apiMessages";

export type BackendBattleResultRecord = BattleResultRecordResponseDto;

type BackendBattleResultsResponse = Partial<Record<keyof BattleResultListResponseDto, unknown>>;

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
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const request: BattleResultListAPIMessageRequest = {
      ...(options?.handle ? { handle: options.handle } : {}),
      ...(options?.battleId ? { battleId: options.battleId } : {}),
      ...(typeof options?.limit === "number" && Number.isFinite(options.limit) ? { limit: Math.trunc(options.limit) } : {})
    };

    const response = await postBattleResultListAPIMessage(request, normalizeBattleResultsResponse, {
      timeoutMs: BATTLE_RESULTS_TIMEOUT_MS,
      cache: "no-store"
    });
    return response?.ok ? response.payload : null;
  } catch {
    return null;
  }
}

function normalizeBattleResultsResponse(payload: unknown): BackendBattleResultRecord[] | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as BackendBattleResultsResponse;
  if (!Array.isArray(value.results)) {
    return null;
  }

  return normalizeRequiredArray(value.results, normalizeRemoteBattleResultRecord);
}

/** 中文名：加载本地战斗results（loadLocalBattleResults）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

  const resultId = readRequiredString(record.resultId);
  const battleId = readRequiredString(record.battleId);
  const handle = readRequiredString(record.handle);
  const displayName = readRequiredString(record.displayName);
  const finishedAt = readNumberField(record.finishedAt);
  const finishedAtLabel = readStringField(record.finishedAtLabel);
  const durationMs = readNumberField(record.durationMs);
  const score = readNumberField(record.score);
  const placement = readNullableNumberField(record.placement);
  const ratingBefore = readNumberField(record.ratingBefore);
  const ratingDelta = readNumberField(record.ratingDelta);
  const ratingAfter = readNumberField(record.ratingAfter);
  const resultLabel = readStringField(record.resultLabel);
  const modeLabel = readStringField(record.modeLabel);
  const mapLabel = readStringField(record.mapLabel);
  const highlightLine = readStringField(record.highlightLine);
  const playersLine = readStringField(record.playersLine);
  const timelineHint = readStringField(record.timelineHint);
  const currentLoadout = readNullableStringField(record.currentLoadout);

  if (
    !resultId ||
    !battleId ||
    !handle ||
    !displayName ||
    finishedAt === null ||
    finishedAtLabel === null ||
    durationMs === null ||
    score === null ||
    typeof record.aliveAtEnd !== "boolean" ||
    placement === undefined ||
    ratingBefore === null ||
    ratingDelta === null ||
    ratingAfter === null ||
    resultLabel === null ||
    modeLabel === null ||
    mapLabel === null ||
    highlightLine === null ||
    playersLine === null ||
    timelineHint === null ||
    currentLoadout === undefined
  ) {
    return null;
  }

  return {
    resultId,
    battleId,
    handle,
    displayName,
    finishedAt,
    finishedAtLabel,
    durationMs,
    score,
    placement,
    aliveAtEnd: record.aliveAtEnd,
    ratingBefore,
    ratingDelta,
    ratingAfter,
    resultLabel,
    modeLabel,
    mapLabel,
    highlightLine,
    playersLine,
    timelineHint,
    currentLoadout
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
  const resultId = normalizeResultId(record.resultId, battleId, handle);

  return normalizeRemoteBattleResultRecord({
    resultId,
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
  "resultId",
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

function normalizeRequiredArray<T>(
  values: unknown[],
  normalize: (value: unknown) => T | null
): T[] | null {
  const normalized: T[] = [];
  for (const value of values) {
    const item = normalize(value);
    if (item === null) {
      return null;
    }

    normalized.push(item);
  }

  return normalized;
}

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

function readRequiredString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readStringField(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function readNullableStringField(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "string" ? value : undefined;
}

function readNumberField(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readNullableNumberField(value: unknown): number | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}
