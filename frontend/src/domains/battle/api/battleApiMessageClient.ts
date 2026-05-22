import { buildApiUrl, normalizeApiBase } from "../../../shared/api/apiUrl";

export const BATTLE_API_MESSAGES = {
  queueJoin: "battlequeuejoin",
  queueStatus: "battlequeuestatus",
  queueLeave: "battlequeueleave",
  roomSnapshot: "battleroomsnapshot",
  roomHeartbeat: "battleroomheartbeat",
  stateRead: "battlestateread",
  command: "battlecommand",
  resultList: "battleresultlist",
  resultRecord: "battleresultrecord"
} as const;

export type BattleApiMessageName = (typeof BATTLE_API_MESSAGES)[keyof typeof BATTLE_API_MESSAGES];

export interface BattleApiMessageResponse<TPayload> {
  ok: boolean;
  status: number;
  payload: TPayload | null;
}

export interface BattleApiVector {
  x: number;
  y: number;
}

export interface BattleQueueJoinAPIMessageRequest {
  handle: string;
  sessionToken: string;
  queueRequestId?: string;
  rating?: string;
  skin?: string;
}

export interface BattleQueueStatusAPIMessageRequest {
  ticketId: string;
}

export interface BattleQueueLeaveAPIMessageRequest {
  ticketId: string;
}

export interface BattleRoomSnapshotAPIMessageRequest {
  roomId: string;
}

export interface BattleRoomHeartbeatAPIMessageRequest {
  roomId: string;
  ticketId?: string;
  handle?: string;
}

export interface BattleStateReadAPIMessageRequest {
  battleId: string;
}

export interface BattleCommandAPIMessageRequest {
  battleId: string;
  playerId: string;
  ticketId: string;
  clientTick: number;
  clientCommandSeq: number;
  movement: BattleApiVector;
  aim: BattleApiVector;
  primaryHeld: boolean;
  sprint: boolean;
  reloadPressed: boolean;
  castDash: boolean;
  castBlink: boolean;
  castFreeze: boolean;
  pointerWorld: BattleApiVector | null;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}

export interface BattleResultListAPIMessageRequest {
  handle?: string;
  battleId?: string;
  limit?: number;
}

export interface BattleResultRecordAPIMessageRequest {
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

interface BattleApiMessageOptions {
  timeoutMs?: number;
  keepalive?: boolean;
  cache?: RequestCache;
}

type BattleApiMessageDecoder<TPayload> = (payload: unknown) => TPayload | null;

type BattleApiMessageRequest =
  | BattleQueueJoinAPIMessageRequest
  | BattleQueueStatusAPIMessageRequest
  | BattleQueueLeaveAPIMessageRequest
  | BattleRoomSnapshotAPIMessageRequest
  | BattleRoomHeartbeatAPIMessageRequest
  | BattleStateReadAPIMessageRequest
  | BattleCommandAPIMessageRequest
  | BattleResultListAPIMessageRequest
  | BattleResultRecordAPIMessageRequest;

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");

export function battleApiMessageUrl(apiName: BattleApiMessageName): string {
  return buildApiUrl(BATTLE_API_BASE, `/${apiName}`);
}

export function postBattleQueueJoinAPIMessage<TPayload>(
  request: BattleQueueJoinAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueJoin, request, decoder, options);
}

export function postBattleQueueStatusAPIMessage<TPayload>(
  request: BattleQueueStatusAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueStatus, request, decoder, options);
}

export function postBattleQueueLeaveAPIMessage<TPayload>(
  request: BattleQueueLeaveAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueLeave, request, decoder, options);
}

export function postBattleRoomSnapshotAPIMessage<TPayload>(
  request: BattleRoomSnapshotAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.roomSnapshot, request, decoder, options);
}

export function postBattleRoomHeartbeatAPIMessage<TPayload>(
  request: BattleRoomHeartbeatAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.roomHeartbeat, request, decoder, options);
}

export function postBattleStateReadAPIMessage<TPayload>(
  request: BattleStateReadAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.stateRead, request, decoder, options);
}

export function postBattleCommandAPIMessage<TPayload>(
  request: BattleCommandAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.command, request, decoder, options);
}

export function postBattleResultListAPIMessage<TPayload>(
  request: BattleResultListAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.resultList, request, decoder, options);
}

export function postBattleResultRecordAPIMessage<TPayload>(
  request: BattleResultRecordAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.resultRecord, request, decoder, options);
}

async function postBattleApiMessage<TPayload>(
  apiName: BattleApiMessageName,
  request: BattleApiMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  if (!BATTLE_API_BASE || typeof window === "undefined") {
    return null;
  }

  const controller = new AbortController();
  const timeoutMs = Math.max(0, Math.trunc(options?.timeoutMs ?? 0));
  const timeout =
    timeoutMs > 0
      ? window.setTimeout(() => {
          controller.abort();
        }, timeoutMs)
      : null;

  try {
    const response = await fetch(battleApiMessageUrl(apiName), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      cache: options?.cache,
      keepalive: options?.keepalive,
      signal: controller.signal
    });
    const rawPayload = await response.json().catch(() => null);

    return {
      ok: response.ok,
      status: response.status,
      payload: decoder(rawPayload)
    };
  } catch {
    return null;
  } finally {
    if (timeout !== null) {
      window.clearTimeout(timeout);
    }
  }
}
