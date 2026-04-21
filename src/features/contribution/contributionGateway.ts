import { getCurrentAuthHandle, getCurrentAuthUser } from "../auth/authGateway";
import { getContributionAdjustmentTotals } from "../governance/localAdminActionStore";
import { getDiscussionActivitySummary } from "../forum/localDiscussionStore";
import { getReplayEntries } from "../battle/local/battleTruthStore";
import { getRatingEntries as getLocalRatingEntries } from "../battle/local/battleTruthStore";
import { loadBattleResults, type BackendBattleResultRecord } from "../battle/results/battleResultsApi";
import { loadIdentityAccounts, type IdentityAccountSummary } from "../identity/identityApi";

const HAS_REMOTE_CONTRIBUTION_SOURCE = Boolean(
  (import.meta.env.VITE_AUTH_API_BASE ?? "").trim() ||
    (import.meta.env.VITE_BATTLE_API_BASE ?? "").trim()
);

export interface ContributionEntry {
  rank: number;
  handle: string;
  score: number;
  totalActions: number;
}

export interface ContributionSummary {
  handle: string;
  title: string;
  totalActions: number;
  battleCount: number;
  replayCount: number;
  discussionTopics: number;
  discussionReplies: number;
  latestActivityLabel: string;
  highlight: string;
  detail: string;
  recentWork: string[];
}

export function getContributionSummary(): ContributionSummary | null {
  const replayEntries = getReplayEntries();
  const discussion = getDiscussionActivitySummary();
  const totalActions = replayEntries.length + discussion.topicCount + discussion.replyCount;

  if (totalActions === 0) {
    return null;
  }

  const latestBattle = replayEntries[0]?.finishedAtLabel ?? null;
  const latestDiscussion =
    discussion.lastUpdatedAt === null ? null : formatRelativeTime(discussion.lastUpdatedAt);

  return {
    handle: getCurrentAuthHandle(),
    title: buildContributionTitle(replayEntries.length, discussion.topicCount, discussion.replyCount),
    totalActions,
    battleCount: replayEntries.length,
    replayCount: replayEntries.length,
    discussionTopics: discussion.topicCount,
    discussionReplies: discussion.replyCount,
    latestActivityLabel: latestDiscussion ?? latestBattle ?? "刚刚开始",
    highlight: buildHighlightLine(replayEntries.length, discussion.topicCount, discussion.replyCount),
    detail: buildDetailLine(replayEntries.length, discussion.topicCount, discussion.replyCount),
    recentWork: buildRecentWork(replayEntries.length, discussion.topicCount, discussion.replyCount)
  };
}

export function getContributionEntries(): ContributionEntry[] {
  if (HAS_REMOTE_CONTRIBUTION_SOURCE) {
    return [];
  }

  return applyContributionAdjustments(withCurrentUserContributionEntry(buildContributionEntriesFromLocalData()));
}

export async function loadContributionEntries(): Promise<ContributionEntry[]> {
  const [remoteResults, remoteAccounts] = await Promise.all([
    loadBattleResults({ limit: 100 }),
    loadIdentityAccounts()
  ]);

  if (remoteResults || remoteAccounts) {
    return applyContributionAdjustments(
      buildContributionEntriesFromRemoteData(remoteAccounts ?? [], remoteResults ?? [])
    );
  }

  return getContributionEntries();
}

export async function loadContributionSummary(): Promise<ContributionSummary | null> {
  const handle = getCurrentAuthHandle();
  const remote = await loadBattleResults({ handle, limit: 20 });

  if (!remote || remote.length === 0) {
    return getContributionSummary();
  }

  const discussion = getDiscussionActivitySummary();
  const battleCount = remote.length;
  const replayCount = remote.length;
  const totalActions = battleCount + discussion.topicCount + discussion.replyCount;
  const latest = sortBattleResultsByRecentness(remote)[0];
  const latestDiscussion =
    discussion.lastUpdatedAt === null ? null : formatRelativeTime(discussion.lastUpdatedAt);

  return {
    handle,
    title: buildContributionTitle(battleCount, discussion.topicCount, discussion.replyCount),
    totalActions,
    battleCount,
    replayCount,
    discussionTopics: discussion.topicCount,
    discussionReplies: discussion.replyCount,
    latestActivityLabel: latestDiscussion ?? latest?.finishedAtLabel ?? "刚刚开始",
    highlight: buildHighlightLine(battleCount, discussion.topicCount, discussion.replyCount),
    detail: buildDetailLine(battleCount, discussion.topicCount, discussion.replyCount),
    recentWork: buildRecentWork(battleCount, discussion.topicCount, discussion.replyCount)
  };
}

function applyContributionAdjustments(entries: ContributionEntry[]): ContributionEntry[] {
  const totals = getContributionAdjustmentTotals();

  return entries
    .map((entry) => {
      const adjustment = totals[normalizeHandle(entry.handle)] ?? 0;
      const adjustedTotal = Math.max(0, entry.totalActions + adjustment);

      return {
        ...entry,
        score: adjustedTotal,
        totalActions: adjustedTotal
      };
    })
    .sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle))
    .map((entry, index) => ({
      ...entry,
      rank: index + 1
    }));
}

function buildContributionEntriesFromBattleResults(records: BackendBattleResultRecord[]): ContributionEntry[] {
  const byHandle = new Map<string, { handle: string; totalActions: number }>();

  records.forEach((record) => {
    const normalizedHandle = normalizeHandle(record.handle);
    const bucket = byHandle.get(normalizedHandle) ?? {
      handle: record.handle.trim() || normalizedHandle,
      totalActions: 0
    };

    bucket.totalActions += 1;
    byHandle.set(normalizedHandle, bucket);
  });

  return rankContributionEntries(
    Array.from(byHandle.values()).map((entry) => ({
      rank: 0,
      handle: entry.handle,
      score: entry.totalActions,
      totalActions: entry.totalActions
    }))
  );
}

function buildContributionEntriesFromRemoteData(
  accounts: IdentityAccountSummary[],
  records: BackendBattleResultRecord[]
): ContributionEntry[] {
  const entriesByHandle = new Map<string, ContributionEntry>();

  buildContributionEntriesFromBattleResults(records).forEach((entry) => {
    entriesByHandle.set(normalizeHandle(entry.handle), entry);
  });

  accounts.forEach((account) => {
    const key = normalizeHandle(account.handle);
    if (!entriesByHandle.has(key)) {
      entriesByHandle.set(key, buildDefaultContributionEntry(account.handle));
    }
  });

  return rankContributionEntries(Array.from(entriesByHandle.values()));
}

function buildDefaultContributionEntry(handle: string): ContributionEntry {
  return {
    rank: 0,
    handle,
    score: 0,
    totalActions: 0
  };
}

function buildContributionEntriesFromLocalData(): ContributionEntry[] {
  return getLocalRatingEntries().map((entry) => ({
    rank: 0,
    handle: entry.handle,
    score: entry.matchCount,
    totalActions: entry.matchCount
  }));
}

function withCurrentUserContributionEntry(entries: ContributionEntry[]): ContributionEntry[] {
  const currentUser = getCurrentAuthUser();
  if (!currentUser) {
    return rankContributionEntries(entries);
  }

  const normalizedHandle = normalizeHandle(currentUser.handle);
  const hasCurrentUser = entries.some((entry) => normalizeHandle(entry.handle) === normalizedHandle);
  const merged = hasCurrentUser
    ? entries
    : [
        ...entries,
        {
          rank: 0,
          handle: currentUser.handle,
          score: 0,
          totalActions: 0
        }
      ];

  return rankContributionEntries(merged);
}

function rankContributionEntries(entries: ContributionEntry[]): ContributionEntry[] {
  return [...entries]
    .sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle))
    .map((entry, index) => ({
      ...entry,
      rank: index + 1
    }));
}

function sortBattleResultsByRecentness(records: BackendBattleResultRecord[]): BackendBattleResultRecord[] {
  return [...records].sort((left, right) => {
    if (right.finishedAt !== left.finishedAt) {
      return right.finishedAt - left.finishedAt;
    }

    if (right.ratingAfter !== left.ratingAfter) {
      return right.ratingAfter - left.ratingAfter;
    }

    return left.handle.localeCompare(right.handle);
  });
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function buildContributionTitle(battleCount: number, topicCount: number, replyCount: number): string {
  if (topicCount + replyCount > battleCount) {
    return "社区参与更活跃";
  }

  if (battleCount >= 3) {
    return "连续作战记录";
  }

  return "刚开始留下记录";
}

function buildHighlightLine(battleCount: number, topicCount: number, replyCount: number): string {
  const parts: string[] = [];

  if (battleCount > 0) {
    parts.push(`完成了 ${battleCount} 场对局`);
  }

  if (topicCount > 0) {
    parts.push(`发起了 ${topicCount} 个话题`);
  }

  if (replyCount > 0) {
    parts.push(`写下了 ${replyCount} 条回复`);
  }

  return `${parts.join("；")}。`;
}

function buildDetailLine(battleCount: number, topicCount: number, replyCount: number): string {
  if (topicCount === 0 && replyCount === 0) {
    return "当前贡献主要来自真实打完并归档的对局。";
  }

  if (battleCount === 0) {
    return "当前贡献主要来自社区发言。完成一场战斗后，这里还会继续补全。";
  }

  return "这里展示的是真实留下的战斗记录与社区活动。";
}

function buildRecentWork(battleCount: number, topicCount: number, replyCount: number): string[] {
  const items: string[] = [];

  if (battleCount > 0) {
    items.push(`归档 ${battleCount} 场战报`);
  }

  if (topicCount > 0) {
    items.push(`发起 ${topicCount} 个话题`);
  }

  if (replyCount > 0) {
    items.push(`追加 ${replyCount} 条回复`);
  }

  return items;
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
