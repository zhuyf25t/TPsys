import {
  postBattleQueueJoinAPIMessage,
  postBattleQueueLeaveAPIMessage,
  postBattleQueueStatusAPIMessage,
  type BattleQueueJoinAPIMessageRequest
} from "../../../apis/battle/microservices/queue/api/BattleQueueApiMessageClient";
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
  MatchmakingRoomChatMessage,
  MatchmakingQueueState
} from "./matchmakingQueueTypes";
import { isBattleVisitorHandle } from "../../../objects/battle/objects/core/BattleCoreRules";
import type { BattleModeIdDto } from "../../../objects/battle/microservices/queue/api/shared/BattleLobbySharedApiTypes";
import type { BattleQueueLeaveResponseDto } from "../../../objects/battle/microservices/queue/api/queue/BattleQueueLeaveApiTypes";
import type { BattleQueueSnapshotResponseDto } from "../../../objects/battle/microservices/queue/api/queue/BattleQueueStatusApiTypes";
import { battleModeDisplayLabel } from "../../../objects/battle/objects/core/BattleModeDisplayLabels";
import { BATTLE_RUNTIME_REQUEST_TIMEOUT_MS } from "../BattleRuntimeNetworkConfig";

const QUEUE_REQUEST_TIMEOUT_MS = BATTLE_RUNTIME_REQUEST_TIMEOUT_MS;

export async function joinMatchmakingQueue(input: {
  handle: string;
  sessionToken: string | null;
  modeId: BattleModeIdDto;
  queueRequestId?: string;
  rating?: number;
  avatar?: string;
  skin?: string;
}): Promise<MatchmakingQueueState | null> {
  const normalizedHandle = input.handle.trim();
  const normalizedSessionToken = input.sessionToken?.trim() ?? "";
  const normalizedQueueRequestId = input.queueRequestId?.trim() ?? "";
  const normalizedModeId = normalizeBattleModeId(input.modeId);
  if (!normalizedHandle || !normalizedSessionToken || isBattleVisitorHandle(normalizedHandle)) {
    return null;
  }

  const request: BattleQueueJoinAPIMessageRequest = {
    handle: normalizedHandle,
    sessionToken: normalizedSessionToken,
    modeId: normalizedModeId,
    ...(normalizedQueueRequestId ? { queueRequestId: normalizedQueueRequestId } : {}),
    ...(typeof input.rating === "number" && Number.isFinite(input.rating)
      ? { rating: Math.trunc(input.rating) }
      : {}),
    ...(typeof input.avatar === "string" && input.avatar.trim() ? { avatar: input.avatar.trim() } : {}),
    ...(typeof input.skin === "string" && input.skin.trim() ? { skin: input.skin.trim() } : {})
  };
  const response = await postBattleQueueJoinAPIMessage(request, normalizeQueueState, {
    timeoutMs: QUEUE_REQUEST_TIMEOUT_MS
  });

  return response?.ok ? response.payload : null;
}

export async function loadMatchmakingQueueStatus(ticketId: string): Promise<MatchmakingQueueState | null> {
  const normalizedTicket = ticketId.trim();
  if (!normalizedTicket) {
    return null;
  }

  const response = await postBattleQueueStatusAPIMessage({ ticketId: normalizedTicket }, normalizeQueueState, {
    timeoutMs: QUEUE_REQUEST_TIMEOUT_MS
  });

  return response?.ok ? response.payload : null;
}

/** 中文名：离开matchmaking队列（leaveMatchmakingQueue）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致?*/
export function leaveMatchmakingQueue(ticketId: string): void {
  const normalizedTicket = ticketId.trim();
  if (!normalizedTicket) {
    return;
  }

  void postBattleQueueLeaveAPIMessage(
    { ticketId: normalizedTicket },
    normalizeQueueLeaveResponse,
    { keepalive: true }
  ).catch(() => {
    // Queue leave is best effort; stale in-memory tickets expire on the backend.
  });
}

function normalizeQueueLeaveResponse(payload: unknown): BattleQueueLeaveResponseDto | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const left = (payload as Partial<BattleQueueLeaveResponseDto>).left;
  return typeof left === "boolean" ? { left } : null;
}

export async function refreshMatchmakingRoomPresence(
  currentState: MatchmakingQueueState,
  handle: string
): Promise<MatchmakingQueueState | null> {
  return updateMatchmakingRoomPresence(currentState, handle);
}

export async function updateMatchmakingRoomPresence(
  currentState: MatchmakingQueueState,
  handle: string,
  options?: {
    startPaused?: boolean;
    chatMessage?: string;
  }
): Promise<MatchmakingQueueState | null> {
  const snapshot = await sendRealtimeRoomHeartbeat({
    roomId: currentState.roomId,
    ticketId: currentState.ticketId,
    handle,
    ...(typeof options?.startPaused === "boolean" ? { startPaused: options.startPaused } : {}),
    ...(options?.chatMessage?.trim() ? { chatMessage: options.chatMessage.trim() } : {})
  });

  return snapshot ? mergeRealtimeRoomSnapshot(currentState, snapshot) : null;
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
  const modeId = readBattleModeId(value.modeId);
  const modeLabel = readString(value.modeLabel);
  const mapId = readString(value.mapId);
  const mapLabel = readString(value.mapLabel);
  const phase = readMatchmakingRoomPhase(value.phase);
  const startPaused = readBoolean(value.startPaused) ?? false;
  const pausedRemainingMs = readNumber(value.pausedRemainingMs);
  const hasFinishedAt = Object.prototype.hasOwnProperty.call(value, "finishedAt");
  const hasBattleSession = Object.prototype.hasOwnProperty.call(value, "battleSession");
  const hasPausedRemainingMs = Object.prototype.hasOwnProperty.call(value, "pausedRemainingMs");

  if (
    !ticketId ||
    !playerId ||
    !roomId ||
    !modeId ||
    !modeLabel ||
    !mapId ||
    !mapLabel ||
    startsAt === null ||
    deadline === null ||
    createdAt === null ||
    capacity === null ||
    durationMs === null ||
    serverTime === null ||
    phase === null ||
    !Array.isArray(value.participants)
  ) {
    return null;
  }

  const participants = normalizeRequiredArray(value.participants, normalizeParticipant);
  const chatMessages = Array.isArray(value.chatMessages) ? normalizeRequiredArray(value.chatMessages, normalizeChatMessage) : [];
  const battleSession = normalizeBattleSessionDescriptor(value.battleSession);
  const finishedAt = readNumber(value.finishedAt);
  if (
    participants === null ||
    chatMessages === null ||
    (hasPausedRemainingMs && pausedRemainingMs === null) ||
    (hasFinishedAt && finishedAt === null) ||
    (hasBattleSession && battleSession === null)
  ) {
    return null;
  }
  const queuedHandles = resolveQueuedHandles(participants, battleSession);

  const resolvedModeId = battleSession?.modeId ?? modeId;
  const resolvedModeLabel = battleModeDisplayLabel(resolvedModeId, battleSession?.modeLabel ?? modeLabel);

  return {
    ticketId,
    playerId,
    roomId,
    matchId: battleSession?.battleId ?? roomId,
    modeId: resolvedModeId,
    modeLabel: resolvedModeLabel,
    mapId: battleSession?.mapId ?? mapId,
    mapLabel: battleSession?.mapLabel ?? mapLabel,
    createdAt,
    startsAt,
    deadline,
    serverTime,
    syncedAt,
    participants,
    players: participants,
    queuedHandles,
    capacity: Math.max(1, capacity),
    durationMs: Math.max(0, durationMs),
    phase,
    startPaused,
    ...(pausedRemainingMs !== null ? { pausedRemainingMs: Math.max(0, pausedRemainingMs) } : {}),
    chatMessages,
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
  const modeId = battleSession?.modeId ?? snapshot.modeId ?? currentState.modeId;
  const modeLabel = battleModeDisplayLabel(modeId, battleSession?.modeLabel ?? snapshot.modeLabel ?? currentState.modeLabel);
  const mapId = battleSession?.mapId ?? snapshot.mapId ?? currentState.mapId;
  const mapLabel = battleSession?.mapLabel ?? snapshot.mapLabel ?? currentState.mapLabel;

  return {
    ...currentState,
    roomId: snapshot.roomId,
    playerId: currentState.playerId,
    matchId: battleSession?.battleId ?? currentState.matchId,
    modeId,
    modeLabel,
    mapId,
    mapLabel,
    startsAt: snapshot.startsAt,
    deadline: snapshot.deadline,
    serverTime: snapshot.serverTime,
    syncedAt,
    participants,
    players: participants,
    queuedHandles,
    capacity: Math.max(1, snapshot.capacity),
    durationMs: Math.max(0, snapshot.durationMs),
    phase: snapshot.phase,
    startPaused: snapshot.startPaused,
    pausedRemainingMs: snapshot.pausedRemainingMs !== undefined ? Math.max(0, snapshot.pausedRemainingMs) : undefined,
    chatMessages: snapshot.chatMessages,
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

  const hasRating = Object.prototype.hasOwnProperty.call(value, "rating");
  const hasAvatar = Object.prototype.hasOwnProperty.call(value, "avatar");
  const hasSkin = Object.prototype.hasOwnProperty.call(value, "skin");
  const rating = readNumber(value.rating);
  const avatar = readString(value.avatar);
  const skin = readString(value.skin);
  if ((hasRating && rating === null) || (hasAvatar && avatar === null) || (hasSkin && skin === null)) {
    return null;
  }

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

type RemoteMatchmakingQueueStateDto = {
  [Field in keyof BattleQueueSnapshotResponseDto]?: unknown;
};

function normalizeBattleSessionDescriptor(payload: unknown): MatchmakingBattleSessionDescriptor | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingBattleSessionDescriptor> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const modeId = readBattleModeId(value.modeId);
  const modeLabel = readString(value.modeLabel);
  const mapId = readString(value.mapId);
  const mapLabel = readString(value.mapLabel);
  const startedAt = readNumber(value.startedAt);
  const serverTime = readNumber(value.serverTime);
  const capacity = readNumber(value.capacity);
  const hasBootstrap = Object.prototype.hasOwnProperty.call(value, "bootstrap");
  if (
    !battleId ||
    !modeId ||
    !modeLabel ||
    !mapId ||
    !mapLabel ||
    startedAt === null ||
    serverTime === null ||
    capacity === null ||
    !Array.isArray(value.roster)
  ) {
    return null;
  }

  const roster = normalizeRequiredArray(value.roster, normalizeBattleSessionRosterEntry)?.sort(
    (left, right) => left.seat - right.seat
  );
  const bootstrap = normalizeBattleSessionBootstrap(value.bootstrap);
  if (typeof roster === "undefined" || roster === null || (hasBootstrap && bootstrap === null)) {
    return null;
  }

  return {
    battleId,
    modeId,
    modeLabel: battleModeDisplayLabel(modeId, modeLabel),
    mapId,
    mapLabel,
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

  const seats = normalizeRequiredArray(value.seats, normalizeBattleSessionBootstrapSeat)?.sort(
    (left, right) => left.seat - right.seat
  );
  if (typeof seats === "undefined" || seats === null) {
    return null;
  }

  return { seats };
}

function normalizeChatMessage(payload: unknown): MatchmakingRoomChatMessage | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<MatchmakingRoomChatMessage> & Record<string, unknown>;
  const messageId = readString(value.messageId);
  const authorPlayerId = readString(value.authorPlayerId);
  const authorHandle = readString(value.authorHandle);
  const body = readString(value.body);
  const createdAt = readNumber(value.createdAt);
  if (!messageId || !authorPlayerId || !authorHandle || !body || createdAt === null) {
    return null;
  }

  return {
    messageId,
    authorPlayerId,
    authorHandle,
    body,
    createdAt
  };
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
  const hasRating = Object.prototype.hasOwnProperty.call(value, "rating");
  const hasAvatar = Object.prototype.hasOwnProperty.call(value, "avatar");
  const hasSkin = Object.prototype.hasOwnProperty.call(value, "skin");
  if ((hasRating && rating === null) || (hasAvatar && avatar === null) || (hasSkin && skin === null)) {
    return null;
  }

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
  const hasRating = Object.prototype.hasOwnProperty.call(value, "rating");
  const hasAvatar = Object.prototype.hasOwnProperty.call(value, "avatar");
  const hasSkin = Object.prototype.hasOwnProperty.call(value, "skin");
  if ((hasRating && rating === null) || (hasAvatar && avatar === null) || (hasSkin && skin === null)) {
    return null;
  }

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
    modeId: battleSession.modeId,
    modeLabel: battleModeDisplayLabel(battleSession.modeId, battleSession.modeLabel),
    mapId: battleSession.mapId,
    mapLabel: battleSession.mapLabel,
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

function readMatchmakingRoomPhase(value: unknown): MatchmakingQueueState["phase"] | null {
  return value === "waiting" || value === "active" || value === "finished" || value === "unknown" ? value : null;
}

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

function normalizeBattleModeId(value: string): BattleModeIdDto {
  const modeId = value.trim();
  return modeId === "default" || modeId === "autumn" || modeId === "winter" || modeId === "normal"
    ? modeId
    : "default";
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readBattleModeId(value: unknown): BattleModeIdDto | null {
  const modeId = readString(value);
  return modeId === "default" || modeId === "autumn" || modeId === "winter" || modeId === "normal" ? modeId : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readBoolean(value: unknown): boolean | null {
  return typeof value === "boolean" ? value : null;
}
