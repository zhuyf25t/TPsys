import { getRatingEntries as getLocalRatingEntries } from "../battle/local/battleTruthStore";
import { loadBattleResults, type BackendBattleResultRecord } from "../battle/results/battleResultsApi";
import { getCurrentAuthUser } from "../auth/authGateway";
import { loadIdentityAccounts, type IdentityAccountSummary } from "../identity/identityApi";

const HAS_REMOTE_RATING_SOURCE = Boolean(
  (import.meta.env.VITE_AUTH_API_BASE ?? "").trim() ||
    (import.meta.env.VITE_BATTLE_API_BASE ?? "").trim()
);

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

function withCurrentUserEntry(entries: RatingEntry[]): RatingEntry[] {
  const currentUser = getCurrentAuthUser();
  if (!currentUser) {
    return entries;
  }

  const exists = entries.some((entry) => entry.handle.toLowerCase() === currentUser.handle.toLowerCase());
  const merged = exists
    ? entries
    : [
        ...entries,
        {
          rank: 0,
          handle: currentUser.handle,
          score: 1200,
          winRate: "0%",
          title: getRatingTitle(1200),
          highlight: "完成一局后会写入真实评分记录。",
          recentForm: "-",
          matchCount: 0
        }
      ];

  return [...merged]
    .sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle))
    .map((entry, index) => ({ ...entry, rank: index + 1 }));
}

export function getRatingEntries(): RatingEntry[] {
  if (HAS_REMOTE_RATING_SOURCE) {
    return [];
  }

  return withCurrentUserEntry(getLocalRatingEntries());
}

export async function loadRatingEntries(): Promise<RatingEntry[]> {
  const [remoteResults, remoteAccounts] = await Promise.all([
    loadBattleResults({ limit: 100 }),
    loadIdentityAccounts()
  ]);

  if (remoteResults || remoteAccounts) {
    return buildRatingEntriesFromRemoteData(remoteAccounts ?? [], remoteResults ?? []);
  }

  return withCurrentUserEntry(getLocalRatingEntries());
}

export function getRatingEntryByHandle(handle: string): RatingEntry | undefined {
  return withCurrentUserEntry(getLocalRatingEntries()).find(
    (entry) => entry.handle.toLowerCase() === handle.toLowerCase()
  );
}

function buildRatingEntriesFromBattleResults(records: BackendBattleResultRecord[]): RatingEntry[] {
  const byHandle = new Map<string, BackendBattleResultRecord[]>();

  records.forEach((record) => {
    const key = record.handle.trim().toLowerCase();
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
      highlight: latest.placement === 1 ? "最近一局拿下最后幸存者。" : "最近一局已经计入当前评分。",
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
    entriesByHandle.set(normalizeHandle(entry.handle), entry);
  });

  accounts.forEach((account) => {
    const key = normalizeHandle(account.handle);
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
    highlight: "瀹屾垚涓€灞€鍚庝細鍐欏叆鐪熷疄璇勫垎璁板綍銆?",
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

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
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

  return left.handle.localeCompare(right.handle);
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
