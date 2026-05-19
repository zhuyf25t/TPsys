import { getCurrentAuthUser } from "../../identity/api/authGateway";

const STORAGE_KEY = "slay-demo.mail-notifications.v1";

export type LocalMailNotificationKind = "friend" | "governance";
export type LocalFriendRequestStatus = "pending" | "accepted" | "rejected";

export interface LocalMailNotificationEntry {
  id: string;
  ownerHandle: string;
  kind: LocalMailNotificationKind;
  subject: string;
  excerpt: string;
  senderLabel: string;
  unread: boolean;
  important: boolean;
  createdAt: number;
  friendRequestId?: string;
  friendRequestStatus?: LocalFriendRequestStatus;
  friendRequestSourceHandle?: string;
}

interface LocalMailNotificationState {
  version: 1;
  entries: LocalMailNotificationEntry[];
}

export function appendMailNotification(input: {
  ownerHandle: string;
  kind: LocalMailNotificationKind;
  subject: string;
  excerpt: string;
  senderLabel: string;
  important?: boolean;
  unread?: boolean;
  friendRequestId?: string;
  friendRequestStatus?: LocalFriendRequestStatus;
  friendRequestSourceHandle?: string;
}): LocalMailNotificationEntry | null {
  const ownerHandle = normalizeHandle(input.ownerHandle);
  const subject = input.subject.trim();
  const excerpt = input.excerpt.trim();
  const senderLabel = input.senderLabel.trim();
  const friendRequestId = input.friendRequestId?.trim() ?? "";
  const friendRequestSourceHandle = input.friendRequestSourceHandle?.trim() ?? "";

  if (!ownerHandle || !subject || !excerpt || !senderLabel) {
    return null;
  }

  const entry: LocalMailNotificationEntry = {
    id: `notify-${input.kind}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    ownerHandle,
    kind: input.kind,
    subject,
    excerpt,
    senderLabel,
    unread: input.unread ?? true,
    important: Boolean(input.important),
    createdAt: Date.now(),
    ...(friendRequestId ? { friendRequestId } : {}),
    ...(input.friendRequestStatus ? { friendRequestStatus: input.friendRequestStatus } : {}),
    ...(friendRequestSourceHandle ? { friendRequestSourceHandle } : {})
  };

  writeState({
    version: 1,
    entries: [entry, ...readState().entries].slice(0, 120)
  });

  return entry;
}

export function getMailNotifications(ownerHandle?: string | null): LocalMailNotificationEntry[] {
  const visibleOwner = resolveVisibleOwner(ownerHandle);
  if (!visibleOwner) {
    return [];
  }

  return readState()
    .entries.filter((entry) => normalizeHandle(entry.ownerHandle) === visibleOwner)
    .sort((left, right) => right.createdAt - left.createdAt);
}

export function markMailNotificationRead(notificationId: string, ownerHandle?: string | null): boolean {
  const id = notificationId.trim();
  if (!id) {
    return false;
  }

  const visibleOwner = resolveVisibleOwner(ownerHandle);
  if (!visibleOwner) {
    return false;
  }

  const state = readState();
  let changed = false;

  const entries = state.entries.map((entry) => {
    if (entry.id !== id) {
      return entry;
    }

    if (normalizeHandle(entry.ownerHandle) !== visibleOwner || !entry.unread) {
      return entry;
    }

    changed = true;
    return { ...entry, unread: false };
  });

  if (!changed) {
    return false;
  }

  writeState({
    version: 1,
    entries
  });

  return true;
}

export function setMailNotificationFriendRequestStatus(input: {
  ownerHandle: string;
  friendRequestId: string;
  status: LocalFriendRequestStatus;
}): boolean {
  const visibleOwner = resolveVisibleOwner(input.ownerHandle);
  const friendRequestId = input.friendRequestId.trim();
  if (!visibleOwner || !friendRequestId) {
    return false;
  }

  const state = readState();
  let changed = false;

  const entries = state.entries.map((entry) => {
    if (
      normalizeHandle(entry.ownerHandle) !== visibleOwner ||
      entry.friendRequestId !== friendRequestId ||
      entry.friendRequestStatus === input.status
    ) {
      return entry;
    }

    changed = true;
    return {
      ...entry,
      unread: false,
      friendRequestStatus: input.status
    };
  });

  if (!changed) {
    return false;
  }

  writeState({
    version: 1,
    entries
  });

  return true;
}

function resolveVisibleOwner(ownerHandle?: string | null): string {
  const currentHandle = normalizeHandle(getCurrentAuthUser()?.handle ?? "");
  if (!currentHandle) {
    return "";
  }

  const requestedHandle = normalizeHandle(ownerHandle ?? currentHandle);
  if (!requestedHandle || requestedHandle !== currentHandle) {
    return "";
  }

  return currentHandle;
}

function readState(): LocalMailNotificationState {
  if (typeof window === "undefined") {
    return { version: 1, entries: [] };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { version: 1, entries: [] };
    }

    const parsed = JSON.parse(raw) as Partial<LocalMailNotificationState>;
    return {
      version: 1,
      entries: Array.isArray(parsed.entries) ? parsed.entries.filter(isMailNotificationEntry) : []
    };
  } catch {
    return { version: 1, entries: [] };
  }
}

function writeState(state: LocalMailNotificationState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function isMailNotificationEntry(value: Partial<LocalMailNotificationEntry>): value is LocalMailNotificationEntry {
  return (
    typeof value.id === "string" &&
    typeof value.ownerHandle === "string" &&
    (value.kind === "friend" || value.kind === "governance") &&
    typeof value.subject === "string" &&
    typeof value.excerpt === "string" &&
    typeof value.senderLabel === "string" &&
    typeof value.unread === "boolean" &&
    typeof value.important === "boolean" &&
    typeof value.createdAt === "number" &&
    (value.friendRequestId === undefined || typeof value.friendRequestId === "string") &&
    (value.friendRequestStatus === undefined ||
      value.friendRequestStatus === "pending" ||
      value.friendRequestStatus === "accepted" ||
      value.friendRequestStatus === "rejected") &&
    (value.friendRequestSourceHandle === undefined || typeof value.friendRequestSourceHandle === "string")
  );
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
