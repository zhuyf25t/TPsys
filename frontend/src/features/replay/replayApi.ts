import { buildApiUrl, normalizeApiBase } from "../api/apiUrl";
import { backfillLocalBattleTruthToBackend } from "../battle/local/battleResultSync";
import { hasMeaningfulReplayFrames } from "./replayRecorder";
import type { ReplayFrame } from "./replayTypes";

const REPLAY_API_BASE = normalizeApiBase(
  import.meta.env.VITE_REPLAY_API_BASE ?? import.meta.env.VITE_BATTLE_API_BASE ?? "",
  "/api"
);
const BACKEND_HEALTH_TIMEOUT_MS = 1_250;

export interface ReplayBackendCatalogItem {
  replayId: string;
  battleId: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
  finishedAt: number;
  finishedAtLabel: string;
  mapLabel: string;
  highlightLine: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  score: number;
  placement: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  ratingBefore?: number | null;
  ratingAfter?: number | null;
  ratingDelta?: number | null;
  frameCount: number;
  playbackAvailable: boolean;
}

export interface ReplayBackendPlaybackItem extends ReplayBackendCatalogItem {
  handle: string;
  displayName: string;
  currentLoadout: string | null;
  frames: ReplayFrame[];
}

export interface ReplaySyncPayload {
  replayId: string;
  battleId: string;
  handle: string;
  displayName: string;
  finishedAt: number;
  finishedAtLabel: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
  mapLabel: string;
  highlightLine: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  score: number;
  placement: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  currentLoadout: string | null;
  frameCount: number;
  playbackAvailable: boolean;
  frames: ReplayFrame[];
}

export async function syncReplayToBackend(payload: ReplaySyncPayload): Promise<boolean> {
  if (typeof window === "undefined") {
    return false;
  }

  try {
    const frames = Array.isArray(payload.frames) ? payload.frames : [];
    const playbackAvailable = hasMeaningfulReplayFrames(frames);
    const response = await fetch(buildApiUrl(REPLAY_API_BASE, "/replay/catalog"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      keepalive: true,
      body: JSON.stringify({
        ...payload,
        frameCount: frames.length,
        playbackAvailable,
        frames,
        framesJson: JSON.stringify(frames)
      })
    });

    return response.ok;
  } catch {
    return false;
  }
}

export async function loadReplayCatalog(): Promise<ReplayBackendCatalogItem[] | null> {
  if (typeof window === "undefined") {
    return null;
  }

  await backfillLocalBattleTruthToBackend();

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), BACKEND_HEALTH_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(REPLAY_API_BASE, "/replay/catalog"), {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json()) as { replays?: unknown[] } | null;
      return Array.isArray(payload?.replays) ? payload.replays.map(normalizeReplayCatalogItem).filter(isReplayCatalogItem) : null;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

export async function loadReplayPlayback(id: string): Promise<ReplayBackendPlaybackItem | null> {
  if (typeof window === "undefined" || !id.trim()) {
    return null;
  }

  await backfillLocalBattleTruthToBackend();

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), BACKEND_HEALTH_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(REPLAY_API_BASE, `/replay/catalog/${encodeURIComponent(id.trim())}`), {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json()) as { replay?: unknown } | null;
      const replay = normalizeReplayPlaybackItem(payload?.replay);
      return replay;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

function normalizeReplayCatalogItem(value: unknown): ReplayBackendCatalogItem | null {
  if (!isRecord(value)) {
    return null;
  }

  if (!hasFields(value, REPLAY_CATALOG_FIELDS)) {
    return null;
  }

  const replayId = readString(value.replayId);
  if (!replayId) {
    return null;
  }

  const battleId = readString(value.battleId);
  const title = readString(value.title);
  const modeLabel = readString(value.modeLabel);
  const resultLabel = readString(value.resultLabel);
  const finishedAt = readOptionalNumber(value.finishedAt) ?? readTimestampFromId(replayId);
  const finishedAtLabel = readString(value.finishedAtLabel);
  const mapLabel = readString(value.mapLabel);
  const highlightLine = readString(value.highlightLine);
  const coverLabel = readString(value.coverLabel);
  const playersLine = readString(value.playersLine);
  const timelineHint = readString(value.timelineHint);
  const score = readNumber(value.score);
  const placement = readNullableNumber(value.placement);
  const durationMs = readNumber(value.durationMs);
  const aliveAtEnd = readBoolean(value.aliveAtEnd);
  const thumbnailDataUrl = readNullableString(value.thumbnailDataUrl);
  const ratingBefore = readNullableNumber(value.ratingBefore);
  const ratingAfter = readNullableNumber(value.ratingAfter);
  const ratingDelta = readNullableNumber(value.ratingDelta);
  const frameCount = readNumber(value.frameCount);
  const playbackAvailableValue = readBoolean(value.playbackAvailable);
  if (
    battleId === null ||
    title === null ||
    modeLabel === null ||
    resultLabel === null ||
    finishedAtLabel === null ||
    mapLabel === null ||
    highlightLine === null ||
    coverLabel === null ||
    playersLine === null ||
    timelineHint === null ||
    score === null ||
    placement === undefined ||
    durationMs === null ||
    aliveAtEnd === null ||
    thumbnailDataUrl === undefined ||
    frameCount === null ||
    playbackAvailableValue === null
  ) {
    return null;
  }

  const playbackAvailable = Boolean(value.playbackAvailable) && frameCount >= 2;
  return {
    replayId,
    battleId,
    title,
    modeLabel,
    resultLabel,
    finishedAt,
    finishedAtLabel,
    mapLabel,
    highlightLine,
    coverLabel,
    playersLine,
    timelineHint,
    score,
    placement,
    durationMs,
    aliveAtEnd,
    thumbnailDataUrl,
    ratingBefore: ratingBefore === undefined ? undefined : ratingBefore,
    ratingAfter: ratingAfter === undefined ? undefined : ratingAfter,
    ratingDelta: ratingDelta === undefined ? undefined : ratingDelta,
    frameCount,
    playbackAvailable
  };
}

function normalizeReplayPlaybackItem(value: unknown): ReplayBackendPlaybackItem | null {
  const catalogItem = normalizeReplayCatalogItem(value);
  if (!catalogItem || !isRecord(value)) {
    return null;
  }

  if (!hasFields(value, REPLAY_PLAYBACK_FIELDS) || !Array.isArray(value.frames)) {
    return null;
  }

  const handle = readString(value.handle);
  const displayName = readString(value.displayName);
  const currentLoadout = readNullableString(value.currentLoadout);
  if (!handle || !displayName || currentLoadout === undefined) {
    return null;
  }

  const frames = value.frames as ReplayFrame[];
  return {
    ...catalogItem,
    handle,
    displayName,
    currentLoadout,
    frameCount: frames.length,
    playbackAvailable: catalogItem.playbackAvailable,
    frames
  };
}

function isReplayCatalogItem(value: ReplayBackendCatalogItem | null): value is ReplayBackendCatalogItem {
  return value !== null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function hasFields<T extends string>(value: Record<string, unknown>, fields: readonly T[]): boolean {
  return fields.every((field) => Object.prototype.hasOwnProperty.call(value, field));
}

function readString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function readNullableString(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "string" ? value : undefined;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readOptionalNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readTimestampFromId(id: string): number {
  const match = id.match(/(\d{10,})/);
  return match ? Number(match[1]) : 0;
}

function readNullableNumber(value: unknown): number | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function readBoolean(value: unknown): boolean | null {
  return typeof value === "boolean" ? value : null;
}

const REPLAY_CATALOG_FIELDS = [
  "replayId",
  "battleId",
  "title",
  "modeLabel",
  "resultLabel",
  "finishedAt",
  "finishedAtLabel",
  "mapLabel",
  "highlightLine",
  "coverLabel",
  "playersLine",
  "timelineHint",
  "score",
  "placement",
  "durationMs",
  "aliveAtEnd",
  "thumbnailDataUrl",
  "frameCount",
  "playbackAvailable"
] satisfies (keyof ReplayBackendCatalogItem)[];

const REPLAY_PLAYBACK_FIELDS = [
  ...REPLAY_CATALOG_FIELDS,
  "handle",
  "displayName",
  "currentLoadout",
  "frames"
] satisfies (keyof ReplayBackendPlaybackItem)[];
