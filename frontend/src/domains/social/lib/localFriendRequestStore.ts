import { getCurrentAuthUser } from "../../identity/api/authGateway";

const STORAGE_KEY = "slay-demo.social.friend-requests.v1";

export type FriendRequestStatus = "pending" | "accepted" | "rejected";

export interface FriendRequestRecord {
  id: string;
  sourceHandle: string;
  targetHandle: string;
  createdAt: number;
  status: FriendRequestStatus;
  respondedAt?: number;
}

interface FriendRequestState {
  version: 1;
  requests: FriendRequestRecord[];
}

export interface FriendRequestResult {
  created: boolean;
  alreadySent: boolean;
  request: FriendRequestRecord | null;
}

export function getFriendRequestStatus(sourceHandle: string, targetHandle: string): FriendRequestRecord | null {
  const source = normalizeHandle(sourceHandle);
  const target = normalizeHandle(targetHandle);
  if (!source || !target) {
    return null;
  }

  return readState().requests.find(
    (request) => normalizeHandle(request.sourceHandle) === source && normalizeHandle(request.targetHandle) === target
  ) ?? null;
}

export function listFriendRequestRecords(ownerHandle?: string | null): FriendRequestRecord[] {
  const owner = normalizeHandle(ownerHandle ?? getCurrentAuthUser()?.handle ?? "");
  if (!owner) {
    return [];
  }

  return readState()
    .requests.filter(
      (request) => normalizeHandle(request.sourceHandle) === owner || normalizeHandle(request.targetHandle) === owner
    )
    .sort((left, right) => right.createdAt - left.createdAt || right.id.localeCompare(left.id));
}

export function sendFriendRequest(input: { sourceHandle?: string; targetHandle: string }): FriendRequestResult {
  const currentUser = getCurrentAuthUser();
  const sourceHandle = normalizeHandle(input.sourceHandle ?? currentUser?.handle ?? "");
  const targetHandle = normalizeHandle(input.targetHandle);

  if (!sourceHandle || !targetHandle || sourceHandle === targetHandle) {
    return { created: false, alreadySent: false, request: null };
  }

  const existing = getFriendRequestStatus(sourceHandle, targetHandle);
  if (existing) {
    return { created: false, alreadySent: true, request: existing };
  }

  const request: FriendRequestRecord = {
    id: `friend-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    sourceHandle,
    targetHandle,
    createdAt: Date.now(),
    status: "pending"
  };

  writeState({
    version: 1,
    requests: [request, ...readState().requests].slice(0, 200)
  });

  return { created: true, alreadySent: false, request };
}

export function rememberFriendRequestLocally(request: FriendRequestRecord): FriendRequestResult {
  const normalizedRequest = normalizeRequestRecord(request);
  if (!normalizedRequest) {
    return { created: false, alreadySent: false, request: null };
  }

  const existing = getFriendRequestStatus(normalizedRequest.sourceHandle, normalizedRequest.targetHandle);
  if (existing) {
    return { created: false, alreadySent: true, request: existing };
  }

  writeState({
    version: 1,
    requests: [normalizedRequest, ...readState().requests].slice(0, 200)
  });

  return { created: true, alreadySent: false, request: normalizedRequest };
}

export function respondToFriendRequest(input: {
  requestId: string;
  actorHandle: string;
  decision: Exclude<FriendRequestStatus, "pending">;
  sourceHandle?: string;
}): { ok: boolean; request: FriendRequestRecord | null } {
  const requestId = input.requestId.trim();
  const actorHandle = normalizeHandle(input.actorHandle);
  const sourceHandle = normalizeHandle(input.sourceHandle ?? "");
  if (!requestId || !actorHandle) {
    return { ok: false, request: null };
  }

  const state = readState();
  const existing = state.requests.find((request) => request.id === requestId) ?? null;
  if (existing && normalizeHandle(existing.targetHandle) !== actorHandle) {
    return { ok: false, request: null };
  }

  const respondedAt = Date.now();
  const updated: FriendRequestRecord | null = existing
    ? {
        ...existing,
        status: existing.status === "pending" ? input.decision : existing.status,
        respondedAt: existing.respondedAt ?? respondedAt
      }
    : sourceHandle
      ? {
          id: requestId,
          sourceHandle,
          targetHandle: actorHandle,
          createdAt: respondedAt,
          status: input.decision,
          respondedAt
        }
      : null;

  if (!updated) {
    return { ok: false, request: null };
  }

  const requests = existing
    ? state.requests.map((request) => (request.id === requestId ? updated : request))
    : [updated, ...state.requests].slice(0, 200);

  writeState({
    version: 1,
    requests
  });

  return { ok: true, request: updated };
}

function readState(): FriendRequestState {
  if (typeof window === "undefined") {
    return { version: 1, requests: [] };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { version: 1, requests: [] };
    }

    const parsed = JSON.parse(raw) as Partial<FriendRequestState>;
    return {
      version: 1,
      requests: Array.isArray(parsed.requests)
        ? parsed.requests.map((value) => normalizeRequestRecord(value as Partial<FriendRequestRecord>)).filter(isPresent)
        : []
    };
  } catch {
    return { version: 1, requests: [] };
  }
}

function writeState(state: FriendRequestState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function normalizeRequestRecord(value: Partial<FriendRequestRecord>): FriendRequestRecord | null {
  if (
    typeof value.id === "string" &&
    typeof value.sourceHandle === "string" &&
    typeof value.targetHandle === "string" &&
    typeof value.createdAt === "number"
  ) {
    const status = normalizeStatus(value.status);
    return {
      id: value.id,
      sourceHandle: value.sourceHandle,
      targetHandle: value.targetHandle,
      createdAt: value.createdAt,
      status,
      ...(typeof value.respondedAt === "number" ? { respondedAt: value.respondedAt } : {})
    };
  }

  return null;
}

function normalizeStatus(status: unknown): FriendRequestStatus {
  return status === "accepted" || status === "rejected" ? status : "pending";
}

function isPresent<T>(value: T | null): value is T {
  return value !== null;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
