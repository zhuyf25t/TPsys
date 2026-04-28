import { buildApiUrl, normalizeApiBase } from "../api/apiUrl";
import { getMailEntries, markMailRead } from "../battle/local/battleTruthStore";
import { backfillLocalBattleTruthToBackend } from "../battle/local/battleResultSync";
import { getCurrentAuthSessionToken, getCurrentAuthUser } from "../auth/authGateway";

export type FriendRequestMailStatus = "pending" | "accepted" | "rejected";

export interface MailSummary {
  id: string;
  relatedMailIds?: string[];
  sourceBattleId?: string;
  backendSyncDisabled?: boolean;
  subject: string;
  excerpt: string;
  kind: "system" | "battle" | "reward" | "friend" | "governance";
  unread: boolean;
  important: boolean;
  senderLabel: string;
  receivedLabel: string;
  sourceLabel?: string;
  sourcePath?: string;
  friendRequestId?: string;
  friendRequestStatus?: FriendRequestMailStatus;
  friendRequestSourceHandle?: string;
  governanceActorHandle?: string;
  governanceTargetPath?: string;
  governanceTargetLabel?: string;
}

const MAILS_API_BASE = normalizeApiBase(
  import.meta.env.VITE_MAILS_API_BASE ?? import.meta.env.VITE_AUTH_API_BASE ?? "",
  "/api"
);
const HAS_REMOTE_MAIL_SOURCE = true;
const REQUEST_TIMEOUT_MS = 5_000;
const MERGED_BATTLE_RESULT_SUBJECT = "\u6218\u6597\u7ed3\u7b97\u4e0e\u8bc4\u5206\u66f4\u65b0";
const OPEN_REPLAY_SOURCE_LABEL = "查看回放";
export const MAIL_SUMMARIES_CHANGED_EVENT = "slay-demo:mails:changed";
export const REMOTE_MAIL_REFRESH_INTERVAL_MS = 15_000;
let remoteMailSummariesCache: { ownerHandle: string; summaries: MailSummary[] } | null = null;

export function isRemoteMailSourceConfigured(): boolean {
  return HAS_REMOTE_MAIL_SOURCE;
}

export function getMailSummaries(ownerHandle?: string | null): MailSummary[] {
  const resolvedOwner = resolveVisibleMailOwner(ownerHandle);
  if (!resolvedOwner) {
    return [];
  }

  const localSummaries = getLocalBattleMailSummaries(resolvedOwner);
  if (!HAS_REMOTE_MAIL_SOURCE) {
    return localSummaries;
  }

  const cachedRemote = remoteMailSummariesCache && normalizeHandle(remoteMailSummariesCache.ownerHandle) === normalizeHandle(resolvedOwner)
    ? remoteMailSummariesCache.summaries
    : null;
  return mergeMailSummaries(localSummaries, cachedRemote ?? []);
}

export function getLocalBattleMailSummaries(ownerHandle?: string | null): MailSummary[] {
  if (!resolveVisibleMailOwner(ownerHandle)) {
    return [];
  }

  return mergeBattleRatingMailSummaries(getMailEntries().map((mail) => toBattleMailSummary(mail)));
}

export async function loadMergedMailSummaries(ownerHandle?: string | null): Promise<MailSummary[] | null> {
  const resolvedOwner = resolveVisibleMailOwner(ownerHandle);
  if (!resolvedOwner) {
    return [];
  }

  const localSummaries = getLocalBattleMailSummaries(resolvedOwner);
  if (!HAS_REMOTE_MAIL_SOURCE) {
    return localSummaries;
  }

  await backfillLocalBattleTruthToBackend();
  const remoteSummaries = await loadRemoteMailSummaries(resolvedOwner);
  if (remoteSummaries !== null) {
    return mergeMailSummaries(localSummaries, remoteSummaries);
  }

  return localSummaries;
}

export function markMailAsRead(mailId: string, ownerHandle?: string | null): boolean {
  const normalizedId = mailId.trim();
  if (!normalizedId) {
    return false;
  }

  const resolvedOwner = resolveVisibleMailOwner(ownerHandle);
  if (!resolvedOwner) {
    return false;
  }

  const changed = normalizedId.startsWith("battle:") ? markMailRead(normalizedId.slice("battle:".length)) : false;

  if (changed) {
    dispatchMailSummariesChanged(resolvedOwner);
  }

  return changed;
}

export async function loadRemoteMailSummaries(ownerHandle?: string | null): Promise<MailSummary[] | null> {
  const resolvedOwner = resolveVisibleMailOwner(ownerHandle);
  if (!resolvedOwner) {
    return [];
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(MAILS_API_BASE, "/mails", { ownerHandle: resolvedOwner }), {
        method: "GET",
        headers: buildSessionHeaders(),
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

      const visibleOwner = resolveVisibleMailOwner(resolvedOwner);
      if (!visibleOwner) {
        return [];
      }

      const summaries = mergeBattleRatingMailSummaries(payload.mails
        .filter(isRemoteMailRecord)
        .filter((mail) => normalizeHandle(mail.ownerHandle) === normalizeHandle(visibleOwner))
        .map((mail) => toRemoteMailSummary(mail)));
      writeRemoteMailCache(visibleOwner, summaries);
      dispatchMailSummariesChanged(visibleOwner);
      return [...summaries];
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

export async function markMailAsReadRemote(ownerHandle: string, mailId: string): Promise<boolean> {
  const resolvedOwner = resolveVisibleMailOwner(ownerHandle);
  const resolvedMailId = mailId.trim();
  if (!resolvedOwner || !resolvedMailId) {
    return false;
  }

  if (isLocalMailId(resolvedMailId)) {
    return false;
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(MAILS_API_BASE, "/mails/read"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...buildSessionHeaders() },
        signal: controller.signal,
        body: JSON.stringify({ ownerHandle: resolvedOwner, mailId: resolvedMailId })
      });

      if (response.ok) {
        updateRemoteMailCacheReadStatus(resolvedOwner, resolvedMailId);
        dispatchMailSummariesChanged(resolvedOwner);
        return true;
      }

      return false;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return false;
  }
}

function updateRemoteMailCacheReadStatus(ownerHandle: string, mailId: string): void {
  if (!remoteMailSummariesCache || normalizeHandle(remoteMailSummariesCache.ownerHandle) !== normalizeHandle(ownerHandle)) {
    return;
  }

  remoteMailSummariesCache = {
    ...remoteMailSummariesCache,
    summaries: remoteMailSummariesCache.summaries.map((mail) =>
      mail.id === mailId || mail.relatedMailIds?.includes(mailId) ? { ...mail, unread: false } : mail
    )
  };
}

function writeRemoteMailCache(ownerHandle: string, summaries: MailSummary[]): void {
  remoteMailSummariesCache = {
    ownerHandle,
    summaries: [...summaries]
  };
}

function dispatchMailSummariesChanged(ownerHandle: string): void {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(MAIL_SUMMARIES_CHANGED_EVENT, { detail: { ownerHandle } }));
}

function toBattleMailSummary(mail: ReturnType<typeof getMailEntries>[number]): MailSummary {
  return {
    id: `battle:${mail.id}`,
    ...(mail.sourceBattleId ? { sourceBattleId: mail.sourceBattleId } : {}),
    ...(mail.backendSyncDisabled ? { backendSyncDisabled: true } : {}),
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: mail.kind,
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: mail.receivedLabel,
    ...(mail.sourceLabel ? { sourceLabel: mail.sourceLabel } : {}),
    ...(mail.sourcePath ? { sourcePath: mail.sourcePath } : {})
  };
}

function mergeMailSummaries(localSummaries: MailSummary[], remoteSummaries: MailSummary[]): MailSummary[] {
  const normalizedLocalSummaries = mergeBattleRatingMailSummaries(localSummaries);
  const normalizedRemoteSummaries = mergeBattleRatingMailSummaries(remoteSummaries);
  const merged = new Map<string, MailSummary>();
  const remoteBattleMailKeys = new Set(
    normalizedRemoteSummaries.map((mail) => getBattleResultMailKey(mail)).filter((key): key is string => Boolean(key))
  );

  normalizedLocalSummaries.forEach((mail) => {
    const battleMailKey = getBattleResultMailKey(mail);
    if (battleMailKey && remoteBattleMailKeys.has(battleMailKey)) {
      return;
    }

    const key = normalizeStableMailId(mail.id);
    if (key) {
      merged.set(key, mail);
    }
  });

  normalizedRemoteSummaries.forEach((mail) => {
    const key = normalizeStableMailId(mail.id);
    if (key) {
      merged.set(key, mail);
    }
  });

  return mergeBattleRatingMailSummaries(Array.from(merged.values()));
}

function toRemoteMailSummary(mail: RemoteMailRecord): MailSummary {
  const friendRequestId = normalizeOptionalString(mail.friendRequestId);
  const friendRequestSourceHandle = normalizeOptionalString(mail.friendRequestSourceHandle);
  const friendRequestStatus = toFriendRequestMailStatus(mail.friendRequestStatus);
  const governanceContext = mail.kind === "governance" ? getGovernanceMailContext(mail) : null;
  const fallbackBattleId = getRemoteBattleId(mail.id);
  const sourceBattleId = normalizeOptionalString(mail.sourceBattleId) || fallbackBattleId;
  const sourcePath = sourceBattleId ? normalizeOptionalString(mail.sourcePath) || `/replay/${sourceBattleId}` : "";
  const sourceLabel = sourceBattleId ? normalizeOptionalString(mail.sourceLabel) || OPEN_REPLAY_SOURCE_LABEL : "";

  return {
    id: mail.id,
    ...(sourceBattleId ? { sourceBattleId } : {}),
    ...(sourceLabel ? { sourceLabel } : {}),
    ...(sourcePath ? { sourcePath } : {}),
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: toMailKind(mail.kind),
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: formatRelativeTime(mail.createdAt),
    ...(friendRequestId ? { friendRequestId } : {}),
    ...(friendRequestStatus ? { friendRequestStatus } : {}),
    ...(friendRequestSourceHandle ? { friendRequestSourceHandle } : {}),
    ...(governanceContext?.actorHandle ? { governanceActorHandle: governanceContext.actorHandle } : {}),
    ...(governanceContext?.targetPath ? { governanceTargetPath: governanceContext.targetPath } : {}),
    ...(governanceContext?.targetLabel ? { governanceTargetLabel: governanceContext.targetLabel } : {})
  };
}

function getBattleResultMailKey(mail: MailSummary): string | null {
  const normalizedId = normalizeStableMailId(mail.id);
  const resultMail = parseBattleResultMailId(normalizedId, mail.sourceBattleId);
  if (!resultMail) {
    return null;
  }

  const ownerHandle = getMailResultOwnerHandle(normalizedId) ?? getCurrentAuthUser()?.handle?.trim() ?? "";
  if (!ownerHandle) {
    return null;
  }

  return `${resultMail.battleId}:${normalizeHandle(ownerHandle)}`;
}

function mergeBattleRatingMailSummaries(summaries: MailSummary[]): MailSummary[] {
  const slots: Array<{ key: string; mail?: MailSummary }> = [];
  const grouped = new Map<string, MailSummary[]>();

  summaries.forEach((mail) => {
    const key = getBattleResultMailKey(mail);
    if (!key) {
      slots.push({ key: `mail:${mail.id}`, mail });
      return;
    }

    const group = grouped.get(key);
    if (group) {
      group.push(mail);
      return;
    }

    grouped.set(key, [mail]);
    slots.push({ key });
  });

  return slots.map((slot) => {
    if (slot.mail) {
      return slot.mail;
    }

    return mergeBattleResultMailGroup(grouped.get(slot.key) ?? []);
  });
}

function mergeBattleResultMailGroup(group: MailSummary[]): MailSummary {
  const primary =
    group.find((mail) => parseBattleResultMailId(normalizeStableMailId(mail.id), mail.sourceBattleId)?.kind === "battle") ??
    group[0];
  if (!primary || group.length === 1) {
    return primary ?? {
      id: "",
      subject: MERGED_BATTLE_RESULT_SUBJECT,
      excerpt: "",
      kind: "battle",
      unread: false,
      important: false,
      senderLabel: "",
      receivedLabel: ""
    };
  }

  const ratingMail = group.find(
    (mail) => parseBattleResultMailId(normalizeStableMailId(mail.id), mail.sourceBattleId)?.kind === "rating"
  );
  const sourceMail = group.find((mail) => mail.sourcePath?.trim()) ?? primary;
  const relatedMailIds = uniqueStrings(group.flatMap((mail) => [mail.id, ...(mail.relatedMailIds ?? [])]));
  const sourceBattleId = primary.sourceBattleId ?? group.find((mail) => mail.sourceBattleId)?.sourceBattleId;
  const sourcePath = sourceMail.sourcePath?.trim() || undefined;
  const sourceLabel = sourceMail.sourceLabel?.trim() || (sourcePath ? OPEN_REPLAY_SOURCE_LABEL : undefined);

  return {
    ...primary,
    relatedMailIds,
    ...(sourceBattleId ? { sourceBattleId } : {}),
    ...(sourcePath ? { sourcePath } : {}),
    ...(sourceLabel ? { sourceLabel } : {}),
    subject: MERGED_BATTLE_RESULT_SUBJECT,
    excerpt: buildMergedBattleResultExcerpt(primary, ratingMail),
    kind: "battle",
    unread: group.some((mail) => mail.unread),
    important: group.some((mail) => mail.important)
  };
}

function buildMergedBattleResultExcerpt(primary: MailSummary, ratingMail: MailSummary | undefined): string {
  return uniqueStrings([primary.excerpt.trim(), ratingMail?.excerpt.trim() ?? ""]).join(" / ");
}

function uniqueStrings(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];

  values.forEach((value) => {
    const normalized = value.trim();
    if (!normalized || seen.has(normalized)) {
      return;
    }

    seen.add(normalized);
    result.push(normalized);
  });

  return result;
}

function getMailResultOwnerHandle(mailId: string): string | null {
  const resultSeparator = mailId.lastIndexOf(":");
  if (resultSeparator < 0 || resultSeparator === mailId.length - 1) {
    return null;
  }

  return mailId.slice(resultSeparator + 1).trim() || null;
}

function getRemoteBattleId(mailId: string): string | null {
  return parseBattleResultMailId(mailId, undefined)?.battleId ?? null;
}

function parseBattleResultMailId(
  mailId: string,
  explicitBattleId: string | undefined
): { kind: "battle" | "rating"; battleId: string } | null {
  const normalized = mailId.trim();
  const prefixes = [
    ["battle", "mail-battle-"],
    ["rating", "mail-rating-"]
  ] as const;
  const prefix = prefixes.find(([, value]) => normalized.startsWith(value));
  if (!prefix) {
    return null;
  }

  const [, value] = prefix;
  const explicit = explicitBattleId?.trim();
  const resultId = normalized.slice(value.length).trim();
  const resultSeparator = resultId.lastIndexOf(":");
  const battleId = explicit || (resultSeparator >= 0 ? resultId.slice(0, resultSeparator).trim() : resultId);

  return battleId ? { kind: prefix[0], battleId } : null;
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

function toFriendRequestMailStatus(status: unknown): FriendRequestMailStatus | undefined {
  return status === "pending" || status === "accepted" || status === "rejected" ? status : undefined;
}

function normalizeOptionalString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

interface GovernanceMailContext {
  actorHandle?: string;
  targetPath?: string;
  targetLabel?: string;
}

function getGovernanceMailContext(mail: RemoteMailRecord): GovernanceMailContext {
  const parsed = parseGovernanceMailContext(mail.subject, mail.excerpt, mail.senderLabel);
  const actorHandle = normalizeOptionalString(mail.governanceActorHandle) || parsed.actorHandle;
  const targetPath = normalizeOptionalString(mail.governanceTargetPath) || parsed.targetPath;
  const targetLabel = normalizeOptionalString(mail.governanceTargetLabel) || parsed.targetLabel;

  return {
    ...(actorHandle ? { actorHandle } : {}),
    ...(targetPath ? { targetPath } : {}),
    ...(targetLabel ? { targetLabel } : {})
  };
}

function parseGovernanceMailContext(subject: string, excerpt: string, senderLabel: string): GovernanceMailContext {
  const actorHandle = parseHandleFromText(senderLabel) ?? parseHandleFromText(excerpt);
  const sourceMatch = excerpt.match(/(?:来源|來源)[:：]\s*(.+)$/);
  const linkMatch = excerpt.match(/(?:链接|鏈接)[:：]\s*(\/\S+)/);
  const sourceText = sourceMatch?.[1]?.trim() ?? "";
  const parsedSource = parseGovernanceSourceText(sourceText);

  return {
    ...(actorHandle ? { actorHandle } : {}),
    ...(linkMatch?.[1] ? { targetPath: sanitizeGovernancePath(linkMatch[1]) } : {}),
    ...(parsedSource.targetPath ? { targetPath: parsedSource.targetPath } : {}),
    ...(parsedSource.targetLabel ? { targetLabel: parsedSource.targetLabel } : {}),
    ...(!parsedSource.targetLabel && subject ? { targetLabel: stripGovernanceSubjectPrefix(subject) } : {})
  };
}

function parseGovernanceSourceText(sourceText: string): GovernanceMailContext {
  if (!sourceText) {
    return {};
  }

  const tokens = sourceText.split(/\s+/).filter(Boolean);
  const pathIndex = tokens.findIndex((token) => token.startsWith("/"));

  if (pathIndex >= 0) {
    const targetPath = sanitizeGovernancePath(tokens[pathIndex]);
    const targetLabel = tokens.slice(0, pathIndex).join(" ").trim();
    return {
      ...(targetLabel ? { targetLabel } : {}),
      ...(targetPath ? { targetPath } : {})
    };
  }

  return {
    targetLabel: sourceText
  };
}

function parseHandleFromText(text: string): string | undefined {
  const match = text.match(/@([A-Za-z0-9_-]{1,32})/);
  return match?.[1];
}

function sanitizeGovernancePath(path: string): string | undefined {
  const normalized = path.trim().replace(/[)\]）】.,，。；;]+$/u, "");
  return normalized ? normalized : undefined;
}

function stripGovernanceSubjectPrefix(subject: string): string {
  return subject.replace(/^\[待处理\]\s*/u, "").trim();
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

function isLocalMailId(mailId: string): boolean {
  return mailId.startsWith("notify:") || mailId.startsWith("notify-") || mailId.startsWith("battle:");
}

function normalizeStableMailId(mailId: string): string {
  const normalized = mailId.trim();
  if (!normalized) {
    return "";
  }

  return normalized.startsWith("battle:") ? normalized.slice("battle:".length) : normalized;
}

function resolveVisibleMailOwner(ownerHandle?: string | null): string {
  const currentHandle = getCurrentAuthUser()?.handle?.trim() ?? "";
  if (!currentHandle) {
    remoteMailSummariesCache = null;
    return "";
  }

  const requestedHandle = ownerHandle?.trim() || currentHandle;
  if (!requestedHandle || normalizeHandle(requestedHandle) !== normalizeHandle(currentHandle)) {
    return "";
  }

  return currentHandle;
}

function buildSessionHeaders(): Record<string, string> {
  const sessionToken = getCurrentAuthSessionToken();
  return sessionToken ? { Authorization: `Bearer ${sessionToken}` } : {};
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function isRemoteMailRecord(value: Partial<RemoteMailRecord>): value is RemoteMailRecord {
  return (
    typeof value.id === "string" &&
    typeof value.ownerHandle === "string" &&
    typeof value.kind === "string" &&
    typeof value.subject === "string" &&
    typeof value.excerpt === "string" &&
    typeof value.senderLabel === "string" &&
    typeof value.unread === "boolean" &&
    typeof value.important === "boolean" &&
    typeof value.createdAt === "number" &&
    (value.friendRequestId === undefined || typeof value.friendRequestId === "string") &&
    (value.friendRequestStatus === undefined || typeof value.friendRequestStatus === "string") &&
    (value.friendRequestSourceHandle === undefined || typeof value.friendRequestSourceHandle === "string") &&
    (value.sourceBattleId === undefined || typeof value.sourceBattleId === "string") &&
    (value.sourcePath === undefined || typeof value.sourcePath === "string") &&
    (value.sourceLabel === undefined || typeof value.sourceLabel === "string") &&
    (value.governanceActorHandle === undefined || typeof value.governanceActorHandle === "string") &&
    (value.governanceTargetPath === undefined || typeof value.governanceTargetPath === "string") &&
    (value.governanceTargetLabel === undefined || typeof value.governanceTargetLabel === "string") &&
    (value.governanceTargetType === undefined || typeof value.governanceTargetType === "string") &&
    (value.governanceNotificationId === undefined || typeof value.governanceNotificationId === "string")
  );
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
  friendRequestId?: string;
  friendRequestStatus?: string;
  friendRequestSourceHandle?: string;
  sourceBattleId?: string;
  sourcePath?: string;
  sourceLabel?: string;
  governanceActorHandle?: string;
  governanceTargetPath?: string;
  governanceTargetLabel?: string;
  governanceTargetType?: string;
  governanceNotificationId?: string;
}
