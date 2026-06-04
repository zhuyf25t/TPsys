import { buildApiUrl, normalizeApiBase } from "../../system/api/apiUrl";
import { getCurrentAuthSessionToken } from "../identity/authGateway";

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

export interface BattleApiMessageOptions {
  timeoutMs?: number;
  keepalive?: boolean;
  cache?: RequestCache;
}

export type BattleApiMessageDecoder<TPayload> = (payload: unknown) => TPayload | null;

type BattleApiMessageRequestPayload = object;
type BattleApiMessageWireRequest<TRequest extends BattleApiMessageRequestPayload> = TRequest & { userToken?: string };

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");

export function battleApiMessageUrl(apiName: BattleApiMessageName): string {
  return buildApiUrl(BATTLE_API_BASE, `/${apiName}`);
}

export async function postBattleApiMessage<TPayload, TRequest extends BattleApiMessageRequestPayload>(
  apiName: BattleApiMessageName,
  request: TRequest,
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
    const wireRequest = withBattleUserToken(request);
    const response = await fetch(battleApiMessageUrl(apiName), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(wireRequest),
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

function withBattleUserToken<TRequest extends BattleApiMessageRequestPayload>(
  request: TRequest
): BattleApiMessageWireRequest<TRequest> {
  const userToken =
    readOptionalStringField(request, "userToken") ??
    readOptionalStringField(request, "sessionToken") ??
    getCurrentAuthSessionToken()?.trim() ??
    "";

  return userToken ? { ...request, userToken } : request;
}

function readOptionalStringField(request: BattleApiMessageRequestPayload, field: string): string | null {
  const value = (request as Record<string, unknown>)[field];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}
