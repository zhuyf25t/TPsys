import { getCurrentAuthHandle } from "../auth/authGateway";
import { loadBattleResults, type BackendBattleResultRecord } from "../battle/results/battleResultsApi";
import { loadIdentityAccounts, type IdentityAccountSummary } from "../identity/identityApi";
import { loadContributionAdjustments } from "../governance/governanceGateway";

const HAS_REMOTE_CONTRIBUTION_SOURCE = true;
export const REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS = 6_000;

type RemoteContributionAdjustmentRecords = NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>;
type RemoteContributionAdjustmentRecord = RemoteContributionAdjustmentRecords[number];

export interface ContributionEntry {
  rank: number;
  handle: string;
  score: number;
  totalActions: number;
}

export interface ContributionSnapshot {
  score: number;
  totalActions: number;
  battleCount: number;
  adjustmentTotal: number;
  adjustmentCount: number;
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

export function isRemoteContributionSourceConfigured(): boolean {
  return HAS_REMOTE_CONTRIBUTION_SOURCE;
}

export function getContributionSummary(): ContributionSummary | null {
  return null;
}

export function getContributionEntries(): ContributionEntry[] {
  return [];
}

export async function loadContributionEntries(): Promise<ContributionEntry[]> {
  const [remoteResults, remoteAccounts, remoteAdjustments] = await Promise.all([
    loadBattleResults({ limit: 200 }),
    loadIdentityAccounts(),
    loadContributionAdjustments()
  ]);

  if (remoteResults !== null && remoteAccounts !== null && remoteAdjustments !== null) {
    const entries = buildContributionEntriesFromRemoteData(
      remoteAccounts ?? [],
      remoteResults ?? [],
      remoteAdjustments ?? []
    );
    return [...entries];
  }

  return [];
}

export async function loadContributionSummary(): Promise<ContributionSummary | null> {
  const handle = getCurrentAuthHandle();
  const remote = await loadBattleResults({ handle, limit: 20 });

  if (!remote || remote.length === 0) {
    return null;
  }

  return buildContributionSummaryFromBattleResults(handle, remote);
}

export function buildContributionSnapshotForHandle(
  handle: string,
  records: readonly Pick<BackendBattleResultRecord, "handle">[],
  adjustments: readonly RemoteContributionAdjustmentRecord[]
): ContributionSnapshot {
  const key = normalizeHandle(handle);
  const battleCount = records.filter((record) => normalizeHandle(record.handle) === key).length;
  const adjustmentRecords = adjustments.filter((record) => normalizeHandle(record.targetHandle) === key);
  const adjustmentTotal = adjustmentRecords.reduce((total, record) => total + record.delta, 0);
  const totalActions = Math.max(0, battleCount + adjustmentTotal);

  return {
    score: totalActions,
    totalActions,
    battleCount,
    adjustmentTotal,
    adjustmentCount: adjustmentRecords.length
  };
}

function buildContributionSummaryFromBattleResults(
  handle: string,
  records: BackendBattleResultRecord[]
): ContributionSummary {
  const battleCount = records.length;
  const latest = sortBattleResultsByRecentness(records)[0];

  return {
    handle,
    title: buildContributionTitle(battleCount, 0, 0),
    totalActions: battleCount,
    battleCount,
    replayCount: battleCount,
    discussionTopics: 0,
    discussionReplies: 0,
    latestActivityLabel: latest?.finishedAtLabel ?? "最近活动",
    highlight: buildHighlightLine(battleCount, 0, 0),
    detail: buildDetailLine(battleCount, 0, 0),
    recentWork: buildRecentWork(battleCount, 0, 0)
  };
}

function buildContributionEntriesFromRemoteData(
  accounts: IdentityAccountSummary[],
  records: BackendBattleResultRecord[],
  adjustments: RemoteContributionAdjustmentRecord[]
): ContributionEntry[] {
  const entriesByHandle = new Map<string, ContributionEntry>();
  const totals = buildContributionAdjustmentTotals(adjustments);

  buildContributionEntriesFromBattleResults(records).forEach((entry) => {
    entriesByHandle.set(normalizeHandle(entry.handle), entry);
  });

  accounts.forEach((account) => {
    const key = normalizeHandle(account.handle);
    if (!entriesByHandle.has(key)) {
      entriesByHandle.set(key, buildDefaultContributionEntry(account.handle));
    }
  });

  return applyContributionAdjustments(Array.from(entriesByHandle.values()), totals);
}

function applyContributionAdjustments(
  entries: ContributionEntry[],
  totals: Record<string, number> = {}
): ContributionEntry[] {
  const entriesByHandle = new Map(entries.map((entry) => [normalizeHandle(entry.handle), entry]));

  Object.entries(totals).forEach(([handle, adjustment]) => {
    if (adjustment !== 0 && !entriesByHandle.has(handle)) {
      entriesByHandle.set(handle, buildDefaultContributionEntry(handle));
    }
  });

  return Array.from(entriesByHandle.values())
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

function buildDefaultContributionEntry(handle: string): ContributionEntry {
  return {
    rank: 0,
    handle,
    score: 0,
    totalActions: 0
  };
}

export function buildContributionAdjustmentTotals(
  records: readonly RemoteContributionAdjustmentRecord[]
): Record<string, number> {
  return records.reduce<Record<string, number>>((totals, record) => {
    const key = normalizeHandle(record.targetHandle);
    totals[key] = (totals[key] ?? 0) + record.delta;
    return totals;
  }, {});
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

  return `${parts.join("，")}。`;
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
    items.push(`归档 ${battleCount} 场战斗`);
  }

  if (topicCount > 0) {
    items.push(`发起 ${topicCount} 个话题`);
  }

  if (replyCount > 0) {
    items.push(`追加 ${replyCount} 条回复`);
  }

  return items;
}
