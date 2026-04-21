import { getCurrentAuthUser } from "../auth/authGateway";

const STORAGE_KEY = "slay-demo.mail-notifications.v1";

export type LocalMailNotificationKind = "friend" | "governance";

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
}): LocalMailNotificationEntry | null {
  const ownerHandle = normalizeHandle(input.ownerHandle);
  const subject = input.subject.trim();
  const excerpt = input.excerpt.trim();
  const senderLabel = input.senderLabel.trim();

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
    unread: true,
    important: Boolean(input.important),
    createdAt: Date.now()
  };

  writeState({
    version: 1,
    entries: [entry, ...readState().entries].slice(0, 120)
  });

  return entry;
}

export function getMailNotifications(): LocalMailNotificationEntry[] {
  const currentUser = getCurrentAuthUser();
  if (!currentUser) {
    return [];
  }

  const normalizedHandle = normalizeHandle(currentUser.handle);
  return readState()
    .entries.filter((entry) => normalizeHandle(entry.ownerHandle) === normalizedHandle)
    .sort((left, right) => right.createdAt - left.createdAt);
}

export function markMailNotificationRead(notificationId: string): boolean {
  const id = notificationId.trim();
  if (!id) {
    return false;
  }

  const currentUser = getCurrentAuthUser();
  if (!currentUser) {
    return false;
  }

  const normalizedHandle = normalizeHandle(currentUser.handle);
  const state = readState();
  let changed = false;

  const entries = state.entries.map((entry) => {
    if (entry.id !== id) {
      return entry;
    }

    if (normalizeHandle(entry.ownerHandle) !== normalizedHandle || !entry.unread) {
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
    typeof value.createdAt === "number"
  );
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
