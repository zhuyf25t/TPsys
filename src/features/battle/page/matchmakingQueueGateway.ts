import { buildApiUrl, normalizeApiBase } from "../../api/apiUrl";
import type { MatchmakingQueueState } from "./matchmakingQueueTypes";

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const QUEUE_REQUEST_TIMEOUT_MS = 1_250;

export async function joinMatchmakingQueue(handle: string): Promise<MatchmakingQueueState | null> {
  const normalizedHandle = handle.trim();
  if (!BATTLE_API_BASE || !normalizedHandle) {
    return null;
  }

  return fetchQueueState(`${BATTLE_API_BASE}/battle/queue/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ handle: normalizedHandle })
  });
}

export async function loadMatchmakingQueueStatus(ticketId: string): Promise<MatchmakingQueueState | null> {
  const normalizedTicket = ticketId.trim();
  if (!BATTLE_API_BASE || !normalizedTicket) {
    return null;
  }

  const url = buildApiUrl(BATTLE_API_BASE, "/battle/queue/status", { ticket: normalizedTicket });

  return fetchQueueState(url, {
    method: "GET",
    cache: "no-store"
  });
}

export function leaveMatchmakingQueue(ticketId: string): void {
  const normalizedTicket = ticketId.trim();
  if (!BATTLE_API_BASE || !normalizedTicket) {
    return;
  }

  void fetch(`${BATTLE_API_BASE}/battle/queue/leave`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ticket: normalizedTicket }),
    keepalive: true
  }).catch(() => {
    // Queue leave is best effort; stale in-memory tickets expire on the backend.
  });
}

async function fetchQueueState(url: string, init: RequestInit): Promise<MatchmakingQueueState | null> {
  if (typeof window === "undefined") {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), QUEUE_REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    return normalizeQueueState(await response.json());
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeQueueState(payload: unknown): MatchmakingQueueState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingQueueState>;
  if (
    typeof value.ticketId !== "string" ||
    typeof value.matchId !== "string" ||
    typeof value.startsAt !== "number" ||
    typeof value.capacity !== "number" ||
    typeof value.durationMs !== "number" ||
    !Array.isArray(value.players)
  ) {
    return null;
  }

  const players = value.players
    .map((player) => {
      const candidate = player as Partial<MatchmakingQueueState["players"][number]>;
      return typeof candidate.handle === "string" && typeof candidate.joinedAt === "number"
        ? { handle: candidate.handle, joinedAt: candidate.joinedAt }
        : null;
    })
    .filter((player): player is MatchmakingQueueState["players"][number] => player !== null);

  return {
    ticketId: value.ticketId,
    matchId: value.matchId,
    startsAt: value.startsAt,
    players,
    capacity: Math.max(1, value.capacity),
    durationMs: Math.max(0, value.durationMs)
  };
}
