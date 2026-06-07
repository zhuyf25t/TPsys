import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS,
  respondToFriendRequest,
  type FriendRequestDecision,
  type FriendRequestRecord
} from "../../../apis/social/friendRequestGateway";

export type FriendFilter = "all" | "incoming" | "outgoing" | "accepted" | "rejected";
export type FriendRequestActionState = "processing" | "failed";

export interface FriendContact {
  handle: string;
  sinceLabel: string;
  request: FriendRequestRecord;
}

export interface FriendsPageState {
  acceptedContacts: FriendContact[];
  currentHandle: string;
  decideFriendRequest: (request: FriendRequestRecord, decision: FriendRequestDecision) => Promise<void>;
  filteredRequests: FriendRequestRecord[];
  friendFilter: FriendFilter;
  incomingPendingCount: number;
  loadFailed: boolean;
  outgoingPendingCount: number;
  requestActions: Record<string, FriendRequestActionState>;
  requests: FriendRequestRecord[];
  setFriendFilter: (filter: FriendFilter) => void;
}

/** 中文名称：好友页 hook。游戏职责：读取好友请求、整理联系人列表并处理好友申请。 */
export function useFriendsPage(): FriendsPageState {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const currentHandle = currentUser?.handle?.trim() ?? "";
  const [requests, setRequests] = useState<FriendRequestRecord[]>(() => getCachedFriendRequests(currentHandle));
  const [loadFailed, setLoadFailed] = useState(false);
  const [friendFilter, setFriendFilter] = useState<FriendFilter>("all");
  const [requestActions, setRequestActions] = useState<Record<string, FriendRequestActionState>>({});

  const refreshRequests = useCallback(async (): Promise<void> => {
    if (!currentHandle) {
      setRequests([]);
      setLoadFailed(false);
      return;
    }

    const remoteRequests = await loadRemoteFriendRequests(currentHandle);
    if (remoteRequests === null) {
      setRequests(getCachedFriendRequests(currentHandle));
      setLoadFailed(true);
      return;
    }

    setRequests(remoteRequests);
    setLoadFailed(false);
  }, [currentHandle]);

  useEffect(() => {
    setRequests(currentHandle ? getCachedFriendRequests(currentHandle) : []);
    setLoadFailed(false);
    setRequestActions({});
    void refreshRequests();
  }, [currentHandle, refreshRequests]);

  useEffect(() => {
    if (!currentHandle) {
      return;
    }

    const refresh = (): void => {
      void refreshRequests();
    };
    const handleVisibilityChange = (): void => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    };

    window.addEventListener("focus", refresh);
    window.addEventListener(FRIEND_REQUESTS_CHANGED_EVENT, refresh);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    const intervalId = window.setInterval(refresh, REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", refresh);
      window.removeEventListener(FRIEND_REQUESTS_CHANGED_EVENT, refresh);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [currentHandle, refreshRequests]);

  const normalizedCurrentHandle = normalizeHandle(currentHandle);
  const visibleRequests = useMemo(
    () =>
      requests
        .filter((request) => isVisibleToHandle(request, normalizedCurrentHandle))
        .sort((left, right) => compareRequests(left, right, normalizedCurrentHandle)),
    [normalizedCurrentHandle, requests]
  );
  const acceptedContacts = useMemo(() => buildAcceptedContacts(visibleRequests, normalizedCurrentHandle), [normalizedCurrentHandle, visibleRequests]);
  const incomingPendingCount = visibleRequests.filter((request) => isIncomingRequest(request, normalizedCurrentHandle) && request.status === "pending").length;
  const outgoingPendingCount = visibleRequests.filter((request) => !isIncomingRequest(request, normalizedCurrentHandle) && request.status === "pending").length;
  const filteredRequests = visibleRequests.filter((request) => {
    switch (friendFilter) {
      case "incoming":
        return isIncomingRequest(request, normalizedCurrentHandle) && request.status === "pending";
      case "outgoing":
        return !isIncomingRequest(request, normalizedCurrentHandle) && request.status === "pending";
      case "accepted":
        return request.status === "accepted";
      case "rejected":
        return request.status === "rejected";
      case "all":
        return true;
    }
  });

  const decideFriendRequest = useCallback(
    async (request: FriendRequestRecord, decision: FriendRequestDecision): Promise<void> => {
      if (!currentHandle || request.status !== "pending" || !isIncomingRequest(request, normalizeHandle(currentHandle))) {
        return;
      }

      setRequestActions((current) => ({ ...current, [request.id]: "processing" }));
      const result = await respondToFriendRequest({
        requestId: request.id,
        actorHandle: currentHandle,
        decision
      });

      if (!result.ok || !result.request) {
        setRequestActions((current) => ({ ...current, [request.id]: "failed" }));
        return;
      }

      setRequests((current) => current.map((item) => (item.id === result.request?.id ? result.request : item)));
      setRequestActions((current) => {
        const next = { ...current };
        delete next[request.id];
        return next;
      });
      setLoadFailed(false);
      void refreshRequests();
    },
    [currentHandle, refreshRequests]
  );

  return {
    acceptedContacts,
    currentHandle,
    decideFriendRequest,
    filteredRequests,
    friendFilter,
    incomingPendingCount,
    loadFailed,
    outgoingPendingCount,
    requestActions,
    requests: visibleRequests,
    setFriendFilter
  };
}

function buildAcceptedContacts(requests: readonly FriendRequestRecord[], owner: string): FriendContact[] {
  const contacts = new Map<string, FriendContact>();

  requests.forEach((request) => {
    if (request.status !== "accepted") {
      return;
    }

    const peerHandle = resolvePeerHandle(request, owner);
    const normalizedPeerHandle = normalizeHandle(peerHandle);
    const existing = contacts.get(normalizedPeerHandle);
    const sinceTime = request.respondedAt ?? request.createdAt;
    if (existing && (existing.request.respondedAt ?? existing.request.createdAt) >= sinceTime) {
      return;
    }

    contacts.set(normalizedPeerHandle, {
      handle: peerHandle,
      sinceLabel: formatRequestTime(sinceTime),
      request
    });
  });

  return [...contacts.values()].sort((left, right) => left.handle.localeCompare(right.handle));
}

export function formatRequestTime(timestamp: number): string {
  const deltaMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (deltaMinutes < 1) {
    return "刚刚";
  }
  if (deltaMinutes < 60) {
    return `${deltaMinutes} 分钟前`;
  }

  const deltaHours = Math.floor(deltaMinutes / 60);
  if (deltaHours < 24) {
    return `${deltaHours} 小时前`;
  }

  const deltaDays = Math.floor(deltaHours / 24);
  if (deltaDays < 7) {
    return `${deltaDays} 天前`;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(timestamp);
}

export function isIncomingRequest(request: FriendRequestRecord, owner: string): boolean {
  return normalizeHandle(request.targetHandle) === owner;
}

export function resolvePeerHandle(request: FriendRequestRecord, owner: string): string {
  return isIncomingRequest(request, owner) ? request.sourceHandle : request.targetHandle;
}

function compareRequests(left: FriendRequestRecord, right: FriendRequestRecord, owner: string): number {
  const priorityDelta = getRequestPriority(left, owner) - getRequestPriority(right, owner);
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  const rightTime = right.respondedAt ?? right.createdAt;
  const leftTime = left.respondedAt ?? left.createdAt;
  return rightTime - leftTime || right.id.localeCompare(left.id);
}

function getRequestPriority(request: FriendRequestRecord, owner: string): number {
  if (isIncomingRequest(request, owner) && request.status === "pending") {
    return 0;
  }
  if (request.status === "pending") {
    return 1;
  }
  if (request.status === "accepted") {
    return 2;
  }
  return 3;
}

function isVisibleToHandle(request: FriendRequestRecord, owner: string): boolean {
  return Boolean(owner) && (normalizeHandle(request.sourceHandle) === owner || normalizeHandle(request.targetHandle) === owner);
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
