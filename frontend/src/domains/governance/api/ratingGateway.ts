import { loadBattleResults, type BackendBattleResultRecord } from "../../battle/api/battleResultsApi";
import { loadIdentityAccounts, type IdentityAccountSummary } from "../../identity/api/identityApi";
import { isPlayableIdentityHandle, normalizePlayerHandleKey } from "../../identity/objects/identityHandlePolicy";

const HAS_REMOTE_RATING_SOURCE = true;
export const REMOTE_RATING_REFRESH_INTERVAL_MS = 6_000;
let remoteRatingEntriesCache: RatingEntry[] | null = null;

export function isRemoteRatingSourceConfigured(): boolean {
  return HAS_REMOTE_RATING_SOURCE;
}

export interface RatingEntry {
  rank: number;
  handle: string;
  score: number;
  winRate: string;
  title: string;
  highlight: string;
  recentForm: string;
  matchCount: number;
}

export function getRatingEntries(): RatingEntry[] {
  return [];
}

export async function loadRatingEntries(): Promise<RatingEntry[]> {
  remoteRatingEntriesCache = null;
  const [remoteResults, remoteAccounts] = await Promise.all([
    loadBattleResults({ limit: 200 }),
    loadIdentityAccounts()
  ]);

  if (remoteResults !== null) {
    remoteRatingEntriesCache = buildRatingEntriesFromRemoteData(remoteAccounts ?? [], remoteResults ?? []);
    return [...remoteRatingEntriesCache];
  }

  remoteRatingEntriesCache = null;
  return [];
}

export function getRatingEntryByHandle(handle: string): RatingEntry | undefined {
  const key = normalizePlayerHandleKey(handle);
  return remoteRatingEntriesCache?.find((entry) => normalizePlayerHandleKey(entry.handle) === key);
}

function buildRatingEntriesFromBattleResults(records: BackendBattleResultRecord[]): RatingEntry[] {
  const byHandle = new Map<string, BackendBattleResultRecord[]>();

  records.forEach((record) => {
    if (!isPlayableIdentityHandle(record.handle)) {
      return;
    }

    const key = normalizePlayerHandleKey(record.handle);
    const bucket = byHandle.get(key) ?? [];
    bucket.push(record);
    byHandle.set(key, bucket);
  });

  const entries = Array.from(byHandle.values()).map((bucket) => {
    const sorted = [...bucket].sort(compareBattleResultByRecentness);
    const latest = sorted[0];
    const wins = sorted.filter((record) => record.placement === 1).length;
    const recentForm = sorted
      .slice(0, 5)
      .map((record) => (record.placement === 1 ? "W" : "L"))
      .join(" ");

    return {
      rank: 0,
      handle: latest.handle,
      score: latest.ratingAfter,
      winRate: sorted.length > 0 ? `${Math.round((wins / sorted.length) * 100)}%` : "0%",
      title: getRatingTitle(latest.ratingAfter),
      highlight: latest.placement === 1 ? "最近一场拿下最后幸存者。" : "最近一场已经计入当前评分。",
      recentForm,
      matchCount: sorted.length
    } satisfies RatingEntry;
  });

  entries.sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle));
  return entries.map((entry, index) => ({ ...entry, rank: index + 1 }));
}

function buildRatingEntriesFromRemoteData(
  accounts: IdentityAccountSummary[],
  records: BackendBattleResultRecord[]
): RatingEntry[] {
  const entriesByHandle = new Map<string, RatingEntry>();

  buildRatingEntriesFromBattleResults(records).forEach((entry) => {
    entriesByHandle.set(normalizePlayerHandleKey(entry.handle), entry);
  });

  accounts.forEach((account) => {
    if (!isPlayableIdentityHandle(account.handle)) {
      return;
    }

    const key = normalizePlayerHandleKey(account.handle);
    if (!entriesByHandle.has(key)) {
      entriesByHandle.set(key, buildDefaultRatingEntry(account.handle));
    }
  });

  return rankRatingEntries(Array.from(entriesByHandle.values()));
}

function buildDefaultRatingEntry(handle: string): RatingEntry {
  return {
    rank: 0,
    handle,
    score: 1200,
    winRate: "0%",
    title: getRatingTitle(1200),
    highlight: "已在远程身份簿中登记，等待真实战绩刷新。",
    recentForm: "-",
    matchCount: 0
  };
}

function rankRatingEntries(entries: RatingEntry[]): RatingEntry[] {
  return [...entries]
    .sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle))
    .map((entry, index) => ({
      ...entry,
      rank: index + 1
    }));
}

function compareBattleResultByRecentness(
  left: BackendBattleResultRecord,
  right: BackendBattleResultRecord
): number {
  if (right.finishedAt !== left.finishedAt) {
    return right.finishedAt - left.finishedAt;
  }

  if (right.ratingAfter !== left.ratingAfter) {
    return right.ratingAfter - left.ratingAfter;
  }

  return left.resultId.localeCompare(right.resultId);
}

function getRatingTitle(rating: number): string {
  if (rating >= 1500) {
    return "前锋";
  }

  if (rating >= 1350) {
    return "突击手";
  }

  return "竞技新兵";
}
