import { getProfileSummary as getLocalProfileSummary } from "../battle/local/battleTruthStore";
import { loadBattleResults, type BackendBattleResultRecord } from "../battle/results/battleResultsApi";

export interface ProfileSummary {
  handle: string;
  score: number | null;
  winRate: string | null;
  title: string;
  motto: string;
  currentLoadout: string | null;
  recentRecord: string;
  recentMatches: Array<{
    title: string;
    detail: string;
  }>;
}

export function getProfileSummary(handle: string): ProfileSummary | undefined {
  return getLocalProfileSummary(handle);
}

export async function loadProfileSummary(handle: string): Promise<ProfileSummary | undefined> {
  const remote = await loadBattleResults({ handle, limit: 20 });
  if (remote && remote.length > 0) {
    const remoteSummary = buildProfileSummaryFromBattleResults(handle, remote);
    if (remoteSummary) {
      return remoteSummary;
    }
  }

  return getLocalProfileSummary(handle);
}

function buildProfileSummaryFromBattleResults(
  requestedHandle: string,
  records: BackendBattleResultRecord[]
): ProfileSummary | undefined {
  const sorted = [...records].sort(compareBattleResultByRecentness);
  const latest = sorted[0];
  if (!latest) {
    return undefined;
  }

  const resolvedHandle = latest.handle || requestedHandle;
  const wins = sorted.filter((record) => record.placement === 1).length;
  const winRate = sorted.length > 0 ? `${Math.round((wins / sorted.length) * 100)}%` : null;

  return {
    handle: resolvedHandle,
    score: latest.ratingAfter,
    winRate,
    title: getRatingTitle(latest.ratingAfter),
    motto: latest.placement === 1 ? "最近一局拿下最后幸存者。" : "最近一局已经写入个人战报。",
    currentLoadout: latest.currentLoadout,
    recentRecord: `已记录 ${sorted.length} 场对局，最近一局 ${latest.resultLabel}。`,
    recentMatches: sorted.slice(0, 5).map((record) => ({
      title: record.resultLabel,
      detail: `${record.finishedAtLabel} · 得分 ${record.score}${record.placement ? ` · 排名 #${record.placement}` : ""}`
    }))
  };
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
