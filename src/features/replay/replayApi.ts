import { buildApiUrl, normalizeApiBase } from "../api/apiUrl";
import type { ReplayFrame } from "./replayTypes";

const REPLAY_API_BASE = normalizeApiBase(
  import.meta.env.VITE_REPLAY_API_BASE ?? import.meta.env.VITE_BATTLE_API_BASE ?? "",
  "/api"
);
const BACKEND_HEALTH_TTL_MS = 10_000;
const BACKEND_HEALTH_TIMEOUT_MS = 1_250;

interface BackendHealthState {
  healthy: boolean;
  checkedAt: number;
}

let backendHealthState: BackendHealthState | null = null;
let backendHealthProbe: Promise<boolean> | null = null;

export interface ReplayBackendCatalogItem {
  id: string;
  battleId: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
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
  frameCount: number;
  playbackAvailable: boolean;
}

export interface ReplayBackendPlaybackItem extends ReplayBackendCatalogItem {
  handle: string;
  displayName: string;
  finishedAt: number;
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

export function syncReplayToBackend(payload: ReplaySyncPayload): void {
  if (typeof window === "undefined") {
    return;
  }

  void canUseBackend().then((healthy) => {
    if (!healthy) {
      return;
    }

    void fetch(buildApiUrl(REPLAY_API_BASE, "/replay/catalog"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ...payload,
        framesJson: JSON.stringify(payload.frames)
      })
    }).catch(() => undefined);
  });
}

export async function loadReplayCatalog(): Promise<ReplayBackendCatalogItem[] | null> {
  if (typeof window === "undefined") {
    return null;
  }

  if (!(await canUseBackend())) {
    return null;
  }

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

      const payload = (await response.json()) as { replays?: ReplayBackendCatalogItem[] } | null;
      return Array.isArray(payload?.replays) ? payload.replays : null;
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

  if (!(await canUseBackend())) {
    return null;
  }

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

      const payload = (await response.json()) as { replay?: ReplayBackendPlaybackItem } | null;
      return payload?.replay ?? null;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

async function canUseBackend(): Promise<boolean> {
  if (!REPLAY_API_BASE) {
    return false;
  }

  if (backendHealthState && Date.now() - backendHealthState.checkedAt < BACKEND_HEALTH_TTL_MS) {
    return backendHealthState.healthy;
  }

  if (backendHealthProbe) {
    return backendHealthProbe;
  }

  backendHealthProbe = probeBackendHealth().finally(() => {
    backendHealthProbe = null;
  });

  return backendHealthProbe;
}

async function probeBackendHealth(): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), BACKEND_HEALTH_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(REPLAY_API_BASE, "/health"), {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        recordBackendHealth(false);
        return false;
      }

      const payload = (await response.json().catch(() => null)) as { status?: string } | null;
      const healthy = payload?.status === "ok" || response.ok;
      recordBackendHealth(healthy);
      return healthy;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    recordBackendHealth(false);
    return false;
  }
}

function recordBackendHealth(healthy: boolean): void {
  backendHealthState = {
    healthy,
    checkedAt: Date.now()
  };
}
