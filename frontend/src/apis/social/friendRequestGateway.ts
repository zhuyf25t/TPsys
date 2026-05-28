import { buildApiUrl, normalizeApiBase } from "../../system/api/apiUrl";
import { getCurrentAuthSessionToken, getCurrentAuthUser, subscribeAuthState } from "../identity/authGateway";
import type {
  FriendRequestCreateResponseDto,
  FriendRequestDecisionDto,
  FriendRequestListResponseDto,
  FriendRequestRespondResponseDto,
  FriendRequestResponseDto,
  FriendRequestStatusDto
} from "../../objects/social/friendRequestTypes";

const SOCIAL_API_BASE = normalizeApiBase(import.meta.env.VITE_SOCIAL_API_BASE ?? import.meta.env.VITE_AUTH_API_BASE ?? "", "/api");
const REQUEST_TIMEOUT_MS = 5_000;
export const REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS = 15_000;
export const FRIEND_REQUESTS_CHANGED_EVENT = "slay-demo:friend-requests:changed";

const remoteFriendRequestStatus = new Map<string, FriendRequestRecord>();
let remoteFriendRequestCache: { ownerHandle: string; requests: FriendRequestRecord[] } | null = null;
let friendRequestCacheAuthKey: string | null = null;
let refreshInFlight: Promise<void> | null = null;

subscribeAuthState(() => {
  const currentUser = getCurrentAuthUser();
  const currentKey = currentUser ? `${currentUser.handle.trim().toLowerCase()}:${currentUser.sessionToken ?? ""}` : null;
  if (currentKey !== friendRequestCacheAuthKey) {
    remoteFriendRequestStatus.clear();
    remoteFriendRequestCache = null;
    friendRequestCacheAuthKey = currentKey;
    void refreshRemoteFriendRequestCache();
  }
});

if (typeof window !== "undefined") {
  window.addEventListener("focus", () => {
    void refreshRemoteFriendRequestCache();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      void refreshRemoteFriendRequestCache();
    }
  });
  window.setInterval(() => {
    void refreshRemoteFriendRequestCache();
  }, REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS);
}

export type FriendRequestStatus = FriendRequestStatusDto;
export type FriendRequestDelivery = "remote" | "failed";
export type FriendRequestDecision = FriendRequestDecisionDto;

export type FriendRequestRecord = FriendRequestResponseDto;

interface FriendRequestResult {
  created: boolean;
  alreadySent: boolean;
  request: FriendRequestRecord | null;
}

export interface FriendRequestSubmissionResult extends FriendRequestResult {
  delivery: FriendRequestDelivery;
  fallbackReason?: string;
}

export interface FriendRequestResponseResult {
  ok: boolean;
  delivery: "remote" | "failed";
  request: FriendRequestRecord | null;
  fallbackReason?: string;
  error?: string;
}

type RemoteFriendRequestResponse =
  Partial<Record<keyof FriendRequestCreateResponseDto, unknown>> &
  Partial<Record<keyof FriendRequestRespondResponseDto, unknown>>;

type RemoteFriendRequestListResponse = Partial<Record<keyof FriendRequestListResponseDto, unknown>>;

/** 中文名：获取好友请求状态（getFriendRequestStatus）。游戏职责：在前端社交域中组织好友请求和本地社交状态，支撑玩家关系互动。 */
export function getFriendRequestStatus(sourceHandle: string, targetHandle: string): FriendRequestRecord | null {
  const key = friendRequestKey(sourceHandle, targetHandle);
  return remoteFriendRequestStatus.get(key) ?? null;
}

/** 中文名：获取cached好友requests（getCachedFriendRequests）。游戏职责：在前端社交域中组织好友请求和本地社交状态，支撑玩家关系互动。 */
export function getCachedFriendRequests(ownerHandle?: string | null): FriendRequestRecord[] {
  const resolvedOwner = resolveVisibleOwner(ownerHandle);
  const cached = remoteFriendRequestCache;
  if (!resolvedOwner || !cached || normalizeHandle(cached.ownerHandle) !== normalizeHandle(resolvedOwner)) {
    return [];
  }

  return cached.requests.map((request) => ({ ...request }));
}

export async function loadRemoteFriendRequests(ownerHandle?: string | null): Promise<FriendRequestRecord[] | null> {
  const resolvedOwner = resolveVisibleOwner(ownerHandle);
  if (!resolvedOwner) {
    return [];
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(SOCIAL_API_BASE, "/social/friend-requests", { ownerHandle: resolvedOwner }), {
        method: "GET",
        headers: buildSessionHeaders(),
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json().catch(() => null)) as RemoteFriendRequestListResponse | null;
      if (!Array.isArray(payload?.requests)) {
        return null;
      }

      const requests = payload.requests.map(normalizeRemoteFriendRequestRecord).filter(isPresent);
      writeRemoteFriendRequestCache(resolvedOwner, requests);
      return [...requests];
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

export async function sendFriendRequest(input: { sourceHandle?: string; targetHandle: string }): Promise<FriendRequestSubmissionResult> {
  const currentUser = getCurrentAuthUser();
  const sourceHandle = currentUser?.handle ?? "";
  const targetHandle = input.targetHandle;

  if (
    !sourceHandle.trim() ||
    !targetHandle.trim() ||
    (input.sourceHandle?.trim() && normalizeHandle(input.sourceHandle) !== normalizeHandle(sourceHandle))
  ) {
    return failedFriendRequest();
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(`${SOCIAL_API_BASE}/social/friend-requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...buildSessionHeaders() },
        signal: controller.signal,
        body: JSON.stringify({ sourceHandle, targetHandle })
      });

      if (!response.ok) {
        return failedFriendRequest(`http_${response.status}`);
      }

      const payload = (await response.json().catch(() => null)) as RemoteFriendRequestResponse | null;
      const request = normalizeRemoteFriendRequestRecord(payload?.request);
      if (request) {
        upsertRemoteFriendRequestCache(request);
        dispatchFriendRequestsChanged(sourceHandle);
        return {
          created: Boolean(payload?.created),
          alreadySent: Boolean(payload?.alreadySent),
          request,
          delivery: "remote"
        };
      }
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return failedFriendRequest("network_unavailable");
  }

  return failedFriendRequest("invalid_payload");
}

export async function respondToFriendRequest(input: {
  requestId: string;
  actorHandle: string;
  decision: FriendRequestDecision;
  sourceHandle?: string;
}): Promise<FriendRequestResponseResult> {
  const currentUser = getCurrentAuthUser();
  const requestId = input.requestId.trim();
  const actorHandle = currentUser?.handle?.trim() ?? "";
  if (
    !requestId ||
    !actorHandle ||
    (input.actorHandle.trim() && normalizeHandle(input.actorHandle) !== normalizeHandle(actorHandle))
  ) {
    return failedFriendRequestResponse("missing_remote_or_fields");
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(`${SOCIAL_API_BASE}/social/friend-requests/respond`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...buildSessionHeaders() },
        signal: controller.signal,
        body: JSON.stringify({ requestId, actorHandle, decision: input.decision })
      });

      if (!response.ok) {
        return failedFriendRequestResponse(`http_${response.status}`);
      }

      const payload = (await response.json().catch(() => null)) as RemoteFriendRequestResponse | null;
      const request = normalizeRemoteFriendRequestRecord(payload?.request);
      if (!request) {
        return failedFriendRequestResponse("invalid_payload");
      }

      upsertRemoteFriendRequestCache(request);
      dispatchFriendRequestsChanged(actorHandle);
      return {
        ok: true,
        delivery: "remote",
        request
      };
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return failedFriendRequestResponse("network_unavailable");
  }
}

function friendRequestKey(sourceHandle: string, targetHandle: string): string {
  return `${sourceHandle.trim().toLowerCase()}->${targetHandle.trim().toLowerCase()}`;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function buildSessionHeaders(): Record<string, string> {
  const sessionToken = getCurrentAuthSessionToken();
  return sessionToken ? { Authorization: `Bearer ${sessionToken}` } : {};
}

function failedFriendRequest(fallbackReason?: string): FriendRequestSubmissionResult {
  const result: FriendRequestSubmissionResult = {
    created: false,
    alreadySent: false,
    request: null,
    delivery: "failed"
  };
  return fallbackReason ? { ...result, fallbackReason } : result;
}

function failedFriendRequestResponse(error: string): FriendRequestResponseResult {
  return {
    ok: false,
    delivery: "failed",
    request: null,
    error
  };
}

function normalizeRemoteFriendRequestRecord(value: unknown): FriendRequestRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as Partial<Record<keyof FriendRequestRecord, unknown>>;
  const status = normalizeRemoteStatus(record.status);
  if (
    typeof record.id !== "string" ||
    typeof record.sourceHandle !== "string" ||
    typeof record.targetHandle !== "string" ||
    typeof record.createdAt !== "number" ||
    !status ||
    !Object.prototype.hasOwnProperty.call(record, "respondedAt")
  ) {
    return null;
  }

  const respondedAt = record.respondedAt;
  if (respondedAt !== null && typeof respondedAt !== "undefined" && typeof respondedAt !== "number") {
    return null;
  }

  return {
    id: record.id,
    sourceHandle: record.sourceHandle,
    targetHandle: record.targetHandle,
    createdAt: record.createdAt,
    status,
    respondedAt: typeof respondedAt === "number" ? respondedAt : null
  };
}

function normalizeRemoteStatus(status: unknown): FriendRequestStatus | null {
  return status === "pending" || status === "accepted" || status === "rejected" ? status : null;
}

function resolveVisibleOwner(ownerHandle?: string | null): string {
  const currentHandle = getCurrentAuthUser()?.handle?.trim() ?? "";
  if (!currentHandle) {
    remoteFriendRequestCache = null;
    return "";
  }

  const requestedHandle = ownerHandle?.trim() || currentHandle;
  if (normalizeHandle(requestedHandle) !== normalizeHandle(currentHandle)) {
    return "";
  }

  return currentHandle;
}

async function refreshRemoteFriendRequestCache(): Promise<void> {
  if (refreshInFlight) {
    return;
  }

  const ownerHandle = getCurrentAuthUser()?.handle?.trim() ?? "";
  if (!ownerHandle) {
    return;
  }

  refreshInFlight = loadRemoteFriendRequests(ownerHandle)
    .then((requests) => {
      if (requests) {
        writeRemoteFriendRequestCache(ownerHandle, requests);
      }
    })
    .finally(() => {
      refreshInFlight = null;
    });

  await refreshInFlight;
}

function writeRemoteFriendRequestCache(ownerHandle: string, requests: FriendRequestRecord[]): void {
  remoteFriendRequestCache = {
    ownerHandle,
    requests: [...requests]
  };

  remoteFriendRequestStatus.clear();
  for (const request of requests) {
    remoteFriendRequestStatus.set(friendRequestKey(request.sourceHandle, request.targetHandle), request);
  }
}

function upsertRemoteFriendRequestCache(request: FriendRequestRecord): void {
  const normalizedRequest = normalizeRequestRecord(request);
  if (!normalizedRequest) {
    return;
  }

  remoteFriendRequestStatus.set(friendRequestKey(normalizedRequest.sourceHandle, normalizedRequest.targetHandle), normalizedRequest);

  if (!remoteFriendRequestCache) {
    return;
  }

  const owner = normalizeHandle(remoteFriendRequestCache.ownerHandle);
  if (owner !== normalizeHandle(normalizedRequest.sourceHandle) && owner !== normalizeHandle(normalizedRequest.targetHandle)) {
    return;
  }

  const nextRequests = remoteFriendRequestCache.requests.filter((item) => item.id !== normalizedRequest.id);
  nextRequests.unshift(normalizedRequest);
  remoteFriendRequestCache = {
    ownerHandle: remoteFriendRequestCache.ownerHandle,
    requests: nextRequests.slice(0, 200)
  };
}

function dispatchFriendRequestsChanged(ownerHandle: string): void {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(FRIEND_REQUESTS_CHANGED_EVENT, { detail: { ownerHandle } }));
}

function normalizeRequestRecord(value: FriendRequestRecord): FriendRequestRecord | null {
  if (
    typeof value.id !== "string" ||
    typeof value.sourceHandle !== "string" ||
    typeof value.targetHandle !== "string" ||
    typeof value.createdAt !== "number" ||
    !normalizeRemoteStatus(value.status)
  ) {
    return null;
  }

  return {
    id: value.id,
    sourceHandle: value.sourceHandle,
    targetHandle: value.targetHandle,
    createdAt: value.createdAt,
    status: normalizeRemoteStatus(value.status) ?? "pending",
    respondedAt: typeof value.respondedAt === "number" ? value.respondedAt : null
  };
}

function isPresent<T>(value: T | null): value is T {
  return value !== null;
}
