import { getCurrentAuthUser } from "../auth/authGateway";
import { appendMailNotification } from "../mails/localMailNotificationStore";

const STORAGE_KEY = "slay-demo.social.friend-requests.v1";

export interface FriendRequestRecord {
  id: string;
  sourceHandle: string;
  targetHandle: string;
  createdAt: number;
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
    createdAt: Date.now()
  };

  writeState({
    version: 1,
    requests: [request, ...readState().requests].slice(0, 200)
  });

  appendMailNotification({
    ownerHandle: targetHandle,
    kind: "friend",
    subject: "好友申请",
    excerpt: `@${sourceHandle} 想加你为好友。`,
    senderLabel: "好友申请",
    important: false
  });

  return { created: true, alreadySent: false, request };
}

export function rememberFriendRequestLocally(request: FriendRequestRecord): FriendRequestResult {
  if (!request.id || !request.sourceHandle || !request.targetHandle || !Number.isFinite(request.createdAt)) {
    return { created: false, alreadySent: false, request: null };
  }

  const existing = getFriendRequestStatus(request.sourceHandle, request.targetHandle);
  if (existing) {
    return { created: false, alreadySent: true, request: existing };
  }

  writeState({
    version: 1,
    requests: [request, ...readState().requests].slice(0, 200)
  });

  return { created: true, alreadySent: false, request };
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
      requests: Array.isArray(parsed.requests) ? parsed.requests.filter(isFriendRequestRecord) : []
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

function isFriendRequestRecord(value: Partial<FriendRequestRecord>): value is FriendRequestRecord {
  return (
    typeof value.id === "string" &&
    typeof value.sourceHandle === "string" &&
    typeof value.targetHandle === "string" &&
    typeof value.createdAt === "number"
  );
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
