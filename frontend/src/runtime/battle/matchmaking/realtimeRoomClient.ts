import {
  postBattleRoomHeartbeatAPIMessage,
  postBattleRoomSnapshotAPIMessage,
  type BattleRoomHeartbeatAPIMessageRequest
} from "../../../apis/battle/battleApiMessageClient";
import type {
  BattleModeIdDto,
  BattleQueueParticipantResponseDto,
  BattleSessionBootstrapResponseDto,
  BattleSessionBootstrapSeatResponseDto,
  BattleSessionDescriptorResponseDto,
  BattleSessionRosterEntryResponseDto,
  RealtimeRoomSnapshotResponseDto
} from "../../../objects/battle/contracts/apiMessages";
import { battleModeDisplayLabel } from "../battleModeDisplayLabels";

export type RealtimeRoomPhase = "waiting" | "active" | "finished" | "unknown";

export interface RealtimeRoomParticipant extends BattleQueueParticipantResponseDto {}

export interface RealtimeBattleSessionRosterEntry extends BattleSessionRosterEntryResponseDto {}

export interface RealtimeBattleSessionBootstrapSeat extends BattleSessionBootstrapSeatResponseDto {}

export interface RealtimeBattleSessionBootstrap extends BattleSessionBootstrapResponseDto {
  seats: RealtimeBattleSessionBootstrapSeat[];
}

export interface RealtimeBattleSessionDescriptor extends BattleSessionDescriptorResponseDto {
  roster: RealtimeBattleSessionRosterEntry[];
  bootstrap?: RealtimeBattleSessionBootstrap;
}

export interface RealtimeRoomSnapshot extends RealtimeRoomSnapshotResponseDto {
  participants: RealtimeRoomParticipant[];
  battleSession?: RealtimeBattleSessionDescriptor;
}

export interface RealtimeRoomHeartbeatRequest {
  roomId: string;
  ticketId?: string;
  handle?: string;
}

const REALTIME_ROOM_TIMEOUT_MS = 1_250;

export async function loadRealtimeRoomSnapshot(roomId: string): Promise<RealtimeRoomSnapshot | null> {
  const normalizedRoomId = roomId.trim();
  if (!normalizedRoomId || typeof window === "undefined") {
    return null;
  }

  const response = await postBattleRoomSnapshotAPIMessage({ roomId: normalizedRoomId }, normalizeRealtimeRoomSnapshot, {
    timeoutMs: REALTIME_ROOM_TIMEOUT_MS
  });

  return response?.ok ? response.payload : null;
}

export async function sendRealtimeRoomHeartbeat(
  request: RealtimeRoomHeartbeatRequest
): Promise<RealtimeRoomSnapshot | null> {
  const normalizedRoomId = request.roomId.trim();
  if (!normalizedRoomId || typeof window === "undefined") {
    return null;
  }

  const messageRequest: BattleRoomHeartbeatAPIMessageRequest = {
    roomId: normalizedRoomId,
    ...(request.ticketId?.trim() ? { ticketId: request.ticketId.trim() } : {}),
    ...(request.handle?.trim() ? { handle: request.handle.trim() } : {})
  };

  const response = await postBattleRoomHeartbeatAPIMessage(messageRequest, normalizeRealtimeRoomSnapshot, {
    timeoutMs: REALTIME_ROOM_TIMEOUT_MS
  });

  return response?.ok ? response.payload : null;
}

function normalizeRealtimeRoomSnapshot(payload: unknown): RealtimeRoomSnapshot | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeRoomSnapshot> & Record<string, unknown>;
  const roomId = readString(value.roomId);
  const modeId = readBattleModeId(value.modeId);
  const modeLabel = readString(value.modeLabel);
  const mapId = readString(value.mapId);
  const mapLabel = readString(value.mapLabel);
  const serverTime = readNumber(value.serverTime);
  const capacity = readNumber(value.capacity);
  const phase = readRealtimeRoomPhase(value.phase);
  const hasFinishedAt = Object.prototype.hasOwnProperty.call(value, "finishedAt");
  const hasBattleSession = Object.prototype.hasOwnProperty.call(value, "battleSession");

  if (
    !roomId ||
    !modeId ||
    !modeLabel ||
    !mapId ||
    !mapLabel ||
    serverTime === null ||
    capacity === null ||
    phase === null ||
    !Array.isArray(value.participants)
  ) {
    return null;
  }

  const participants = normalizeRequiredArray(value.participants, normalizeParticipant);
  const battleSession = normalizeBattleSessionDescriptor(value.battleSession);
  const finishedAt = readNumber(value.finishedAt);
  if (
    participants === null ||
    (hasFinishedAt && finishedAt === null) ||
    (hasBattleSession && battleSession === null)
  ) {
    return null;
  }

  return {
    roomId,
    modeId,
    modeLabel: battleModeDisplayLabel(modeId, modeLabel),
    mapId,
    mapLabel,
    serverTime,
    participants,
    capacity: Math.max(1, capacity),
    phase,
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

function normalizeBattleSessionBootstrap(payload: unknown): RealtimeBattleSessionBootstrap | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<RealtimeBattleSessionBootstrap> & Record<string, unknown>;
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

function readRealtimeRoomPhase(value: unknown): RealtimeRoomPhase | null {
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
