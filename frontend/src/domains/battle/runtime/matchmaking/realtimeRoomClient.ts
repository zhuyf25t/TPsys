import { buildApiUrl, normalizeApiBase } from "../../../../shared/api/apiUrl";

export type RealtimeRoomPhase = "waiting" | "active" | "finished" | "unknown";

export interface RealtimeRoomParticipant {
  playerId: string;
  handle: string;
  joinedAt: number;
  lastSeen: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface RealtimeBattleSessionRosterEntry {
  seat: number;
  playerId: string;
  handle: string;
  joinedAt: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface RealtimeBattleSessionBootstrapSeat {
  seat: number;
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  joinedAt: number;
  isBot: boolean;
  spawnPointIndex: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface RealtimeBattleSessionBootstrap {
  seats: RealtimeBattleSessionBootstrapSeat[];
}

export interface RealtimeBattleSessionDescriptor {
  battleId: string;
  startedAt: number;
  serverTime: number;
  roster: RealtimeBattleSessionRosterEntry[];
  capacity: number;
  bootstrap?: RealtimeBattleSessionBootstrap;
}

export interface RealtimeRoomSnapshot {
  roomId: string;
  serverTime: number;
  participants: RealtimeRoomParticipant[];
  capacity: number;
  phase: RealtimeRoomPhase;
  finishedAt?: number;
  battleSession?: RealtimeBattleSessionDescriptor;
}

export interface RealtimeRoomHeartbeatRequest {
  roomId: string;
  ticketId?: string;
  handle?: string;
}

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const REALTIME_ROOM_TIMEOUT_MS = 1_250;

export async function loadRealtimeRoomSnapshot(roomId: string): Promise<RealtimeRoomSnapshot | null> {
  const normalizedRoomId = roomId.trim();
  if (!BATTLE_API_BASE || !normalizedRoomId || typeof window === "undefined") {
    return null;
  }

  const url = buildApiUrl(BATTLE_API_BASE, `/battle/rooms/${encodeURIComponent(normalizedRoomId)}/snapshot`);

  return fetchRealtimeRoomSnapshot(url, {
    method: "GET",
    cache: "no-store"
  });
}

export async function sendRealtimeRoomHeartbeat(
  request: RealtimeRoomHeartbeatRequest
): Promise<RealtimeRoomSnapshot | null> {
  const normalizedRoomId = request.roomId.trim();
  if (!BATTLE_API_BASE || !normalizedRoomId || typeof window === "undefined") {
    return null;
  }

  const url = buildApiUrl(BATTLE_API_BASE, `/battle/rooms/${encodeURIComponent(normalizedRoomId)}/heartbeat`);
  const body = {
    ...(request.ticketId?.trim() ? { ticketId: request.ticketId.trim() } : {}),
    ...(request.handle?.trim() ? { handle: request.handle.trim() } : {})
  };

  return fetchRealtimeRoomSnapshot(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}

async function fetchRealtimeRoomSnapshot(url: string, init: RequestInit): Promise<RealtimeRoomSnapshot | null> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), REALTIME_ROOM_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    return normalizeRealtimeRoomSnapshot(await response.json().catch(() => null));
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeRealtimeRoomSnapshot(payload: unknown): RealtimeRoomSnapshot | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeRoomSnapshot> & Record<string, unknown>;
  const roomId = readString(value.roomId);
  const serverTime = readNumber(value.serverTime);
  const capacity = readNumber(value.capacity);

  if (!roomId || serverTime === null || capacity === null || !Array.isArray(value.participants)) {
    return null;
  }

  const participants = value.participants
    .map((participant) => normalizeParticipant(participant))
    .filter((participant): participant is RealtimeRoomParticipant => participant !== null);
  const battleSession = normalizeBattleSessionDescriptor(value.battleSession);
  const finishedAt = readNumber(value.finishedAt);

  return {
    roomId,
    serverTime,
    participants,
    capacity: Math.max(1, capacity),
    phase: normalizePhase(value.phase),
    ...(finishedAt !== null ? { finishedAt } : {}),
    ...(battleSession ? { battleSession } : {})
  };
}

function normalizeBattleSessionDescriptor(payload: unknown): RealtimeBattleSessionDescriptor | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeBattleSessionDescriptor> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const startedAt = readNumber(value.startedAt);
  const serverTime = readNumber(value.serverTime);
  const capacity = readNumber(value.capacity);
  if (!battleId || startedAt === null || serverTime === null || capacity === null || !Array.isArray(value.roster)) {
    return null;
  }

  const roster = value.roster
    .map((entry) => normalizeBattleSessionRosterEntry(entry))
    .filter((entry): entry is RealtimeBattleSessionRosterEntry => entry !== null)
    .sort((left, right) => left.seat - right.seat);
  const bootstrap = normalizeBattleSessionBootstrap(value.bootstrap);

  return {
    battleId,
    startedAt,
    serverTime,
    roster,
    capacity: Math.max(1, capacity),
    ...(bootstrap ? { bootstrap } : {})
  };
}

function normalizeBattleSessionBootstrap(payload: unknown): RealtimeBattleSessionBootstrap | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeBattleSessionBootstrap> & Record<string, unknown>;
  if (!Array.isArray(value.seats)) {
    return null;
  }

  const seats = value.seats
    .map((entry) => normalizeBattleSessionBootstrapSeat(entry))
    .filter((entry): entry is RealtimeBattleSessionBootstrapSeat => entry !== null)
    .sort((left, right) => left.seat - right.seat);

  return seats.length > 0 ? { seats } : null;
}

function normalizeBattleSessionRosterEntry(payload: unknown): RealtimeBattleSessionRosterEntry | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeBattleSessionRosterEntry> & Record<string, unknown>;
  const seat = readNumber(value.seat);
  const playerId = readString(value.playerId);
  const handle = readString(value.handle);
  const joinedAt = readNumber(value.joinedAt);
  if (seat === null || playerId === null || handle === null || joinedAt === null) {
    return null;
  }

  const rating = readNumber(value.rating);
  const avatar = readString(value.avatar);
  const skin = readString(value.skin);

  return {
    seat: Math.max(0, Math.trunc(seat)),
    playerId,
    handle,
    joinedAt,
    ...(rating !== null ? { rating } : {}),
    ...(avatar ? { avatar } : {}),
    ...(skin ? { skin } : {})
  };
}

function normalizeBattleSessionBootstrapSeat(payload: unknown): RealtimeBattleSessionBootstrapSeat | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeBattleSessionBootstrapSeat> & Record<string, unknown>;
  const seat = readNumber(value.seat);
  const playerId = readString(value.playerId);
  const heroId = readString(value.heroId);
  const handle = readString(value.handle);
  const displayName = readString(value.displayName);
  const joinedAt = readNumber(value.joinedAt);
  const spawnPointIndex = readNumber(value.spawnPointIndex);
  if (
    seat === null ||
    playerId === null ||
    heroId === null ||
    handle === null ||
    displayName === null ||
    joinedAt === null ||
    spawnPointIndex === null ||
    typeof value.isBot !== "boolean"
  ) {
    return null;
  }

  const rating = readNumber(value.rating);
  const avatar = readString(value.avatar);
  const skin = readString(value.skin);

  return {
    seat: Math.max(0, Math.trunc(seat)),
    playerId,
    heroId,
    handle,
    displayName,
    joinedAt,
    isBot: value.isBot,
    spawnPointIndex: Math.max(0, Math.trunc(spawnPointIndex)),
    ...(rating !== null ? { rating } : {}),
    ...(avatar ? { avatar } : {}),
    ...(skin ? { skin } : {})
  };
}

function normalizeParticipant(payload: unknown): RealtimeRoomParticipant | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeRoomParticipant> & Record<string, unknown>;
  const playerId = readString(value.playerId);
  const handle = readString(value.handle);
  if (!playerId || !handle) {
    return null;
  }

  const joinedAt = readNumber(value.joinedAt);
  const lastSeen = readNumber(value.lastSeen);
  if (joinedAt === null || lastSeen === null) {
    return null;
  }

  const rating = readNumber(value.rating);
  const avatar = readString(value.avatar);
  const skin = readString(value.skin);

  return {
    playerId,
    handle,
    joinedAt,
    lastSeen,
    ...(rating !== null ? { rating } : {}),
    ...(avatar ? { avatar } : {}),
    ...(skin ? { skin } : {})
  };
}

function normalizePhase(value: unknown): RealtimeRoomPhase {
  return value === "waiting" || value === "active" || value === "finished" ? value : "unknown";
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
