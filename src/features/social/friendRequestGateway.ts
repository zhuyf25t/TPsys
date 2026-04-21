import { normalizeApiBase } from "../api/apiUrl";
import { getCurrentAuthUser } from "../auth/authGateway";
import {
  FriendRequestResult,
  FriendRequestRecord,
  getFriendRequestStatus as getLocalFriendRequestStatus,
  rememberFriendRequestLocally,
  sendFriendRequest as sendFriendRequestLocally
} from "./localFriendRequestStore";

const SOCIAL_API_BASE = normalizeApiBase(
  import.meta.env.VITE_SOCIAL_API_BASE ?? import.meta.env.VITE_AUTH_API_BASE ?? "",
  "/api"
);
const REQUEST_TIMEOUT_MS = 1_250;

interface RemoteFriendRequestResponse {
  created?: boolean;
  alreadySent?: boolean;
  request?: FriendRequestRecord | null;
}

export function getFriendRequestStatus(sourceHandle: string, targetHandle: string) {
  return getLocalFriendRequestStatus(sourceHandle, targetHandle);
}

export async function sendFriendRequest(input: {
  sourceHandle?: string;
  targetHandle: string;
}): Promise<FriendRequestResult> {
  const currentUser = getCurrentAuthUser();
  const sourceHandle = input.sourceHandle ?? currentUser?.handle ?? "";
  const targetHandle = input.targetHandle;

  if (!sourceHandle.trim() || !targetHandle.trim()) {
    return sendFriendRequestLocally(input);
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(`${SOCIAL_API_BASE}/social/friend-requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify({ sourceHandle, targetHandle })
      });

      if (!response.ok) {
        return sendFriendRequestLocally(input);
      }

      const payload = (await response.json().catch(() => null)) as RemoteFriendRequestResponse | null;
      const request =
        payload?.request ??
        ({
          id: `friend-remote-${Date.now()}`,
          sourceHandle,
          targetHandle,
          createdAt: Date.now()
        } satisfies FriendRequestRecord);
      if (request) {
        rememberFriendRequestLocally(request);
        return {
          created: Boolean(payload?.created),
          alreadySent: Boolean(payload?.alreadySent),
          request
        };
      }
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    // Fall back to local persistence below.
  }

  return sendFriendRequestLocally(input);
}
