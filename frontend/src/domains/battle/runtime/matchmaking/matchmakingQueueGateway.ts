import { buildApiUrl, normalizeApiBase } from "../../../../shared/api/apiUrl";
import {
  sendRealtimeRoomHeartbeat,
  type RealtimeBattleSessionBootstrap,
  type RealtimeBattleSessionBootstrapSeat,
  type RealtimeBattleSessionDescriptor,
  type RealtimeBattleSessionRosterEntry,
  type RealtimeRoomParticipant,
  type RealtimeRoomSnapshot
} from "./realtimeRoomClient";
import type {
  MatchmakingBattleSessionBootstrap,
  MatchmakingBattleSessionBootstrapSeat,
  MatchmakingBattleSessionDescriptor,
  MatchmakingBattleSessionRosterEntry,
  MatchmakingQueueParticipant,
  MatchmakingQueueState
} from "./matchmakingQueueTypes";
import { isBattleVisitorHandle } from "../../objects/battleRules";

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const QUEUE_REQUEST_TIMEOUT_MS = 1_250;

export async function joinMatchmakingQueue(input: {
  handle: string;
  sessionToken: string | null;
  queueRequestId?: string;
  rating?: number;
  skin?: string;
}): Promise<MatchmakingQueueState | null> {
  const normalizedHandle = input.handle.trim();
  const normalizedSessionToken = input.sessionToken?.trim() ?? "";
  const normalizedQueueRequestId = input.queueRequestId?.trim() ?? "";
  if (!BATTLE_API_BASE || !normalizedHandle || !normalizedSessionToken || isBattleVisitorHandle(normalizedHandle)) {
    return null;
  }

  return fetchQueueState(buildApiUrl(BATTLE_API_BASE, "/battle/queue/join"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      handle: normalizedHandle,
      sessionToken: normalizedSessionToken,
      ...(normalizedQueueRequestId ? { queueRequestId: normalizedQueueRequestId } : {}),
      ...(typeof input.rating === "number" && Number.isFinite(input.rating)
        ? { rating: String(Math.trunc(input.rating)) }
        : {}),
      ...(typeof input.skin === "string" && input.skin.trim() ? { skin: input.skin.trim() } : {})
    })
  });
}

export async function loadMatchmakingQueueStatus(ticketId: string): Promise<MatchmakingQueueState | null> {
  const normalizedTicket = ticketId.trim();
  if (!BATTLE_API_BASE || !normalizedTicket) {
    return null;
  }

  const url = buildApiUrl(BATTLE_API_BASE, "/battle/queue/status", { ticketId: normalizedTicket });

  return fetchQueueState(url, {
    method: "GET",
    cache: "no-store"
  });
}

/** 中文名：离开matchmaking队列（leaveMatchmakingQueue）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function leaveMatchmakingQueue(ticketId: string): void {
  const normalizedTicket = ticketId.trim();
  if (!BATTLE_API_BASE || !normalizedTicket) {
    return;
  }

  void fetch(buildApiUrl(BATTLE_API_BASE, "/battle/queue/leave"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ticketId: normalizedTicket }),
    keepalive: true
  }).catch(() => {
    // Queue leave is best effort; stale in-memory tickets expire on the backend.
  });
}

export async function refreshMatchmakingRoomPresence(
  currentState: MatchmakingQueueState,
  handle: string
): Promise<MatchmakingQueueState | null> {
  const snapshot = await sendRealtimeRoomHeartbeat({
    roomId: currentState.roomId,
    ticketId: currentState.ticketId,
    handle
  });

  return snapshot ? mergeRealtimeRoomSnapshot(currentState, snapshot) : null;
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

  const syncedAt = Date.now();
  const value = payload as RemoteMatchmakingQueueStateDto;
  const ticketId = readString(value.ticketId);
  const playerId = readString(value.playerId);
  const roomId = readString(value.roomId);
  const startsAt = readNumber(value.startsAt);
  const deadline = readNumber(value.deadline);
  const createdAt = readNumber(value.createdAt);
  const capacity = readNumber(value.capacity);
  const durationMs = readNumber(value.durationMs);
  const serverTime = readNumber(value.serverTime);

  if (
    !ticketId ||
    !playerId ||
    !roomId ||
    startsAt === null ||
    deadline === null ||
    createdAt === null ||
    capacity === null ||
    durationMs === null ||
    !Array.isArray(value.participants)
  ) {
    return null;
  }

  const participants = value.participants
    .map((participant) => normalizeParticipant(participant))
    .filter((participant): participant is MatchmakingQueueParticipant => participant !== null);
  const battleSession = normalizeBattleSessionDescriptor(value.battleSession);
  const queuedHandles = resolveQueuedHandles(participants, battleSession);
  const finishedAt = readNumber(value.finishedAt);

  return {
    ticketId,
    playerId,
    roomId,
    matchId: battleSession?.battleId ?? roomId,
    createdAt,
    startsAt,
    deadline,
    ...(serverTime !== null ? { serverTime, syncedAt } : {}),
    participants,
    players: participants,
    queuedHandles,
    capacity: Math.max(1, capacity),
    durationMs: Math.max(0, durationMs),
    phase: normalizePhase(value.phase),
    ...(finishedAt !== null ? { finishedAt } : {}),
    ...(battleSession ? { battleSession } : {}),
    source: "backend"
  };
}

function mergeRealtimeRoomSnapshot(
  currentState: MatchmakingQueueState,
  snapshot: RealtimeRoomSnapshot
): MatchmakingQueueState {
  const syncedAt = Date.now();
  const participants = snapshot.participants.map(toQueueParticipant);
  const battleSession = toMatchmakingBattleSessionDescriptor(snapshot.battleSession) ?? currentState.battleSession ?? null;
  const queuedHandles = resolveQueuedHandles(participants, battleSession);

  return {
    ...currentState,
    roomId: snapshot.roomId,
    playerId: currentState.playerId,
    matchId: battleSession?.battleId ?? currentState.matchId,
    serverTime: snapshot.serverTime,
    syncedAt,
    participants,
    players: participants,
    queuedHandles,
    capacity: Math.max(1, snapshot.capacity),
    phase: snapshot.phase,
    ...(snapshot.finishedAt !== undefined ? { finishedAt: snapshot.finishedAt } : {}),
    ...(battleSession ? { battleSession } : {}),
    source: "backend"
  };
}

function toQueueParticipant(participant: RealtimeRoomParticipant): MatchmakingQueueParticipant {
  return {
    playerId: participant.playerId,
    handle: participant.handle,
    joinedAt: participant.joinedAt,
    lastSeen: participant.lastSeen,
    ...(participant.rating !== undefined ? { rating: participant.rating } : {}),
    ...(participant.avatar ? { avatar: participant.avatar } : {}),
    ...(participant.skin ? { skin: participant.skin } : {})
  };
}

function normalizeParticipant(payload: unknown): MatchmakingQueueParticipant | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingQueueParticipant> & Record<string, unknown>;
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

interface RemoteMatchmakingQueueStateDto {
  ticketId?: unknown;
  playerId?: unknown;
  roomId?: unknown;
  createdAt?: unknown;
  startsAt?: unknown;
  deadline?: unknown;
  serverTime?: unknown;
  participants?: unknown;
  capacity?: unknown;
  durationMs?: unknown;
  phase?: unknown;
  finishedAt?: unknown;
  battleSession?: unknown;
}

function normalizeBattleSessionDescriptor(payload: unknown): MatchmakingBattleSessionDescriptor | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingBattleSessionDescriptor> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const startedAt = readNumber(value.startedAt);
  const serverTime = readNumber(value.serverTime);
  const capacity = readNumber(value.capacity);
  if (!battleId || startedAt === null || serverTime === null || capacity === null || !Array.isArray(value.roster)) {
    return null;
  }

  const roster = value.roster
    .map((entry) => normalizeBattleSessionRosterEntry(entry))
    .filter((entry): entry is MatchmakingBattleSessionRosterEntry => entry !== null)
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

function normalizeBattleSessionBootstrap(payload: unknown): MatchmakingBattleSessionBootstrap | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingBattleSessionBootstrap> & Record<string, unknown>;
  if (!Array.isArray(value.seats)) {
    return null;
  }

  const seats = value.seats
    .map((entry) => normalizeBattleSessionBootstrapSeat(entry))
    .filter((entry): entry is MatchmakingBattleSessionBootstrapSeat => entry !== null)
    .sort((left, right) => left.seat - right.seat);

  return seats.length > 0 ? { seats } : null;
}

function normalizeBattleSessionRosterEntry(payload: unknown): MatchmakingBattleSessionRosterEntry | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingBattleSessionRosterEntry> & Record<string, unknown>;
  const seat = readNumber(value.seat);
  const playerId = readString(value.playerId);
  const handle = readString(value.handle);
  const joinedAt = readNumber(value.joinedAt);
  if (seat === null || !playerId || !handle || joinedAt === null) {
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

function normalizeBattleSessionBootstrapSeat(payload: unknown): MatchmakingBattleSessionBootstrapSeat | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingBattleSessionBootstrapSeat> & Record<string, unknown>;
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

function toMatchmakingBattleSessionDescriptor(
  battleSession: RealtimeBattleSessionDescriptor | undefined
): MatchmakingBattleSessionDescriptor | null {
  if (!battleSession) {
    return null;
  }

  return {
    battleId: battleSession.battleId,
    startedAt: battleSession.startedAt,
    serverTime: battleSession.serverTime,
    roster: battleSession.roster
      .map((entry) => toMatchmakingBattleSessionRosterEntry(entry))
      .sort((left, right) => left.seat - right.seat),
    capacity: Math.max(1, battleSession.capacity),
    ...(battleSession.bootstrap ? { bootstrap: toMatchmakingBattleSessionBootstrap(battleSession.bootstrap) } : {})
  };
}

function toMatchmakingBattleSessionBootstrap(
  bootstrap: RealtimeBattleSessionBootstrap
): MatchmakingBattleSessionBootstrap {
  return {
    seats: bootstrap.seats
      .map((entry) => toMatchmakingBattleSessionBootstrapSeat(entry))
      .sort((left, right) => left.seat - right.seat)
  };
}

function toMatchmakingBattleSessionRosterEntry(
  entry: RealtimeBattleSessionRosterEntry
): MatchmakingBattleSessionRosterEntry {
  return {
    seat: Math.max(0, Math.trunc(entry.seat)),
    playerId: entry.playerId,
    handle: entry.handle,
    joinedAt: entry.joinedAt,
    ...(entry.rating !== undefined ? { rating: entry.rating } : {}),
    ...(entry.avatar ? { avatar: entry.avatar } : {}),
    ...(entry.skin ? { skin: entry.skin } : {})
  };
}

function toMatchmakingBattleSessionBootstrapSeat(
  entry: RealtimeBattleSessionBootstrapSeat
): MatchmakingBattleSessionBootstrapSeat {
  return {
    seat: Math.max(0, Math.trunc(entry.seat)),
    playerId: entry.playerId,
    heroId: entry.heroId,
    handle: entry.handle,
    displayName: entry.displayName,
    joinedAt: entry.joinedAt,
    isBot: entry.isBot,
    spawnPointIndex: Math.max(0, Math.trunc(entry.spawnPointIndex)),
    ...(entry.rating !== undefined ? { rating: entry.rating } : {}),
    ...(entry.avatar ? { avatar: entry.avatar } : {}),
    ...(entry.skin ? { skin: entry.skin } : {})
  };
}

function resolveQueuedHandles(
  participants: MatchmakingQueueParticipant[],
  battleSession: MatchmakingBattleSessionDescriptor | null
): string[] {
  if (battleSession && battleSession.roster.length > 0) {
    return battleSession.roster.map((entry) => entry.handle);
  }

  if (battleSession?.bootstrap?.seats.length) {
    return battleSession.bootstrap.seats.filter((entry) => !entry.isBot).map((entry) => entry.handle);
  }

  return participants.map((participant) => participant.handle);
}

function normalizePhase(value: unknown): MatchmakingQueueState["phase"] {
  return value === "waiting" || value === "active" || value === "finished" ? value : "unknown";
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
