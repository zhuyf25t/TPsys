import type { ReplayPlayback } from "../objects/replayTypes";
import { compactReplayFrames } from "../objects/replayRecorder";

const STORAGE_KEY = "slay-demo.local-replay-playback.v1";
const MAX_STORED_REPLAYS = 24;
const FALLBACK_STORED_REPLAYS = 6;
const EMERGENCY_STORED_REPLAYS = 1;
const REPLAY_SUMMARY_FRAME_LIMIT = 0;
const INDEXED_DB_NAME = "slay-demo.replay-playbacks";
const INDEXED_DB_VERSION = 1;
const INDEXED_DB_STORE_NAME = "playbacks";
const SUMMARY_CATALOG_READ_LIMIT_BYTES = 900_000;

const memoryReplayCache = new Map<string, ReplayPlayback>();
const pendingReplayPersistPromises = new Map<string, Promise<void>>();

interface StoredReplayPlaybackState {
  version: 2;
  replays: ReplayPlayback[];
}

/** 中文名：保存本地回放playback（saveLocalReplayPlayback）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function saveLocalReplayPlayback(playback: ReplayPlayback): void {
  if (typeof window === "undefined") {
    return;
  }

  const nextReplay = cloneReplayPlayback(playback);
  memoryReplayCache.set(nextReplay.id, nextReplay);

  writeReplaySummaryCatalog(nextReplay);

  const persistPromise = persistReplayPlayback(nextReplay);
  pendingReplayPersistPromises.set(nextReplay.id, persistPromise);
  void persistPromise.finally(() => {
    if (pendingReplayPersistPromises.get(nextReplay.id) === persistPromise) {
      pendingReplayPersistPromises.delete(nextReplay.id);
    }
  });
}

/** 中文名：获取本地回放playbackby标识（getLocalReplayPlaybackById）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getLocalReplayPlaybackById(id: string): ReplayPlayback | undefined {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return undefined;
  }

  const cached = memoryReplayCache.get(normalizedId);
  if (cached) {
    return cloneReplayPlayback(cached);
  }

  const entry = readState().replays.find((replay) => replay.id === normalizedId);
  if (!entry) {
    return undefined;
  }

  return cloneReplayPlayback(entry);
}

export async function loadLocalReplayPlaybackById(id: string): Promise<ReplayPlayback | undefined> {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return undefined;
  }

  const cached = memoryReplayCache.get(normalizedId);
  if (cached) {
    return cloneReplayPlayback(cached);
  }

  const pendingPersist = pendingReplayPersistPromises.get(normalizedId);
  if (pendingPersist) {
    await pendingPersist.catch(() => undefined);
  }

  const persisted = await readReplayPlaybackFromIndexedDb(normalizedId);
  if (!persisted) {
    return undefined;
  }

  const cachedEntry = cloneReplayPlayback(persisted);
  memoryReplayCache.set(cachedEntry.id, cachedEntry);
  return cachedEntry;
}

function readState(): StoredReplayPlaybackState {
  if (typeof window === "undefined") {
    return { version: 2, replays: [] };
  }

  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return { version: 2, replays: [] };
  }
  if (!raw) {
    return { version: 2, replays: [] };
  }
  if (raw.length > SUMMARY_CATALOG_READ_LIMIT_BYTES) {
    clearReplaySummaryCatalog();
    return { version: 2, replays: [] };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<StoredReplayPlaybackState>;
    return {
      version: 2,
      replays: Array.isArray(parsed.replays) ? parsed.replays.map(normalizeReplayPlaybackForSummary) : []
    };
  } catch {
    return { version: 2, replays: [] };
  }
}

function writeReplaySummaryCatalog(nextReplay: ReplayPlayback): void {
  const state = readState();
  const nextReplaySummary = normalizeReplayPlaybackForSummary(nextReplay);
  const existingSummaries = state.replays.filter((entry) => entry.id !== nextReplay.id);

  if (
    tryWriteState({
      version: 2,
      replays: [nextReplaySummary, ...existingSummaries].slice(0, MAX_STORED_REPLAYS)
    })
  ) {
    return;
  }

  if (
    tryWriteState({
      version: 2,
      replays: [nextReplaySummary, ...existingSummaries].slice(0, FALLBACK_STORED_REPLAYS)
    })
  ) {
    return;
  }

  const emergencySummaries = [nextReplaySummary, ...existingSummaries]
    .map(stripReplaySummaryPreview)
    .slice(0, EMERGENCY_STORED_REPLAYS);

  if (tryWriteState({ version: 2, replays: emergencySummaries })) {
    return;
  }

  clearReplaySummaryCatalog();
}

function tryWriteState(state: StoredReplayPlaybackState): boolean {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    return true;
  } catch {
    return false;
  }
}

function clearReplaySummaryCatalog(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // The IndexedDB playback store and memory cache remain the source of full replay frames.
  }
}

function normalizeReplayPlaybackForSummary(playback: ReplayPlayback): ReplayPlayback {
  return {
    ...playback,
    frames: compactReplayFrames(playback.frames, REPLAY_SUMMARY_FRAME_LIMIT)
  };
}

function stripReplaySummaryPreview(playback: ReplayPlayback): ReplayPlayback {
  return {
    ...playback,
    thumbnailDataUrl: null,
    frames: []
  };
}

function cloneReplayPlayback(playback: ReplayPlayback): ReplayPlayback {
  return {
    ...playback,
    frames: playback.frames.map(cloneReplayFrame)
  };
}

async function persistReplayPlayback(playback: ReplayPlayback): Promise<void> {
  const db = await openReplayPlaybackDatabase();
  if (!db) {
    return;
  }

  await new Promise<void>((resolve) => {
    const transaction = db.transaction(INDEXED_DB_STORE_NAME, "readwrite");
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => resolve();
    transaction.objectStore(INDEXED_DB_STORE_NAME).put({
      id: playback.id,
      playback,
      savedAt: Date.now()
    });
  });
}

async function readReplayPlaybackFromIndexedDb(id: string): Promise<ReplayPlayback | undefined> {
  const db = await openReplayPlaybackDatabase();
  if (!db) {
    return undefined;
  }

  return await new Promise<ReplayPlayback | undefined>((resolve) => {
    const transaction = db.transaction(INDEXED_DB_STORE_NAME, "readonly");
    const request = transaction.objectStore(INDEXED_DB_STORE_NAME).get(id);
    request.onsuccess = () => {
      const record = request.result as { playback?: ReplayPlayback } | undefined;
      resolve(record?.playback ? cloneReplayPlayback(record.playback) : undefined);
    };
    request.onerror = () => resolve(undefined);
  });
}

async function openReplayPlaybackDatabase(): Promise<IDBDatabase | null> {
  if (typeof window === "undefined" || typeof window.indexedDB === "undefined") {
    return null;
  }

  return await new Promise<IDBDatabase | null>((resolve) => {
    const request = window.indexedDB.open(INDEXED_DB_NAME, INDEXED_DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(INDEXED_DB_STORE_NAME)) {
        db.createObjectStore(INDEXED_DB_STORE_NAME, { keyPath: "id" });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

function cloneReplayFrame(frame: ReplayPlayback["frames"][number]) {
  return {
    ...frame,
    worldSize: { ...frame.worldSize },
    heroes: frame.heroes.map((hero) => ({
      ...hero,
      position: { ...hero.position }
    })),
    projectiles: frame.projectiles.map((projectile) => ({
      ...projectile,
      position: { ...projectile.position }
    })),
    pickups: frame.pickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    eventMessages: [...frame.eventMessages]
  };
}
