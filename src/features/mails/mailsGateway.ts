import { buildApiUrl, normalizeApiBase } from "../api/apiUrl";
import { getMailEntries, markMailRead } from "../battle/local/battleTruthStore";
import { getCurrentAuthUser } from "../auth/authGateway";
import { getMailNotifications, markMailNotificationRead } from "./localMailNotificationStore";

export interface MailSummary {
  id: string;
  subject: string;
  excerpt: string;
  kind: "system" | "battle" | "reward" | "friend" | "governance";
  unread: boolean;
  important: boolean;
  senderLabel: string;
  receivedLabel: string;
}

const MAILS_API_BASE = normalizeApiBase(
  import.meta.env.VITE_MAILS_API_BASE ?? import.meta.env.VITE_AUTH_API_BASE ?? "",
  "/api"
);
const REQUEST_TIMEOUT_MS = 1_250;

export function getMailSummaries(): MailSummary[] {
  const notifications = getMailNotifications().map((mail) => toMailSummary(`notify:${mail.id}`, mail));
  const battleMails = getMailEntries().map((mail) => toBattleMailSummary(mail));

  return [...notifications, ...battleMails];
}

export function getLocalBattleMailSummaries(): MailSummary[] {
  return getMailEntries().map((mail) => toBattleMailSummary(mail));
}

export async function loadMergedMailSummaries(ownerHandle?: string): Promise<MailSummary[] | null> {
  const remoteSummaries = await loadRemoteMailSummaries(ownerHandle);
  if (!remoteSummaries) {
    return null;
  }

  return [...remoteSummaries, ...getLocalBattleMailSummaries()];
}

export function markMailAsRead(mailId: string): boolean {
  const normalizedId = mailId.trim();
  if (!normalizedId) {
    return false;
  }

  if (normalizedId.startsWith("notify:")) {
    return markMailNotificationRead(normalizedId.slice("notify:".length));
  }

  if (normalizedId.startsWith("battle:")) {
    return markMailRead(normalizedId.slice("battle:".length));
  }

  if (markMailNotificationRead(normalizedId)) {
    return true;
  }

  return markMailRead(normalizedId);
}

export async function loadRemoteMailSummaries(ownerHandle?: string): Promise<MailSummary[] | null> {
  const resolvedOwner = ownerHandle?.trim() || getCurrentAuthUser()?.handle?.trim() || "";
  if (!resolvedOwner) {
    return null;
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(MAILS_API_BASE, "/mails", { owner: resolvedOwner }), {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json().catch(() => null)) as { mails?: RemoteMailRecord[] } | null;
      if (!Array.isArray(payload?.mails)) {
        return null;
      }

      return payload.mails.map((mail) => toRemoteMailSummary(mail));
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

export async function markMailAsReadRemote(ownerHandle: string, mailId: string): Promise<boolean> {
  const resolvedOwner = ownerHandle.trim();
  const resolvedMailId = mailId.trim();
  if (!resolvedOwner || !resolvedMailId) {
    return false;
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(MAILS_API_BASE, "/mails/read"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify({ ownerHandle: resolvedOwner, mailId: resolvedMailId })
      });

      return response.ok;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return false;
  }
}

function toMailSummary(
  id: string,
  mail: {
    subject: string;
    excerpt: string;
    kind: "friend" | "governance";
    unread: boolean;
    important: boolean;
    senderLabel: string;
    receivedLabel?: string;
    createdAt?: number;
  }
): MailSummary {
  return {
    id,
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: mail.kind,
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: mail.receivedLabel ?? formatRelativeTime(mail.createdAt ?? Date.now())
  };
}

function toBattleMailSummary(mail: ReturnType<typeof getMailEntries>[number]): MailSummary {
  return {
    id: `battle:${mail.id}`,
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: mail.kind,
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: mail.receivedLabel
  };
}

function toRemoteMailSummary(mail: RemoteMailRecord): MailSummary {
  return {
    id: mail.id,
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: toMailKind(mail.kind),
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: formatRelativeTime(mail.createdAt)
  };
}

function toMailKind(kind: string): MailSummary["kind"] {
  switch (kind) {
    case "battle":
    case "reward":
    case "friend":
    case "governance":
      return kind;
    default:
      return "system";
  }
}

function formatRelativeTime(timestamp: number): string {
  const deltaMs = Date.now() - timestamp;
  const deltaMinutes = Math.max(0, Math.floor(deltaMs / 60000));

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

interface RemoteMailRecord {
  id: string;
  ownerHandle: string;
  kind: string;
  subject: string;
  excerpt: string;
  senderLabel: string;
  unread: boolean;
  important: boolean;
  createdAt: number;
}
