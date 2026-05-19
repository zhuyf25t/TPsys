import { loadBattleResults, type BackendBattleResultRecord } from "../../battle/api/battleResultsApi";
import { buildContributionSnapshotForHandle } from "../../governance/api/contributionGateway";
import { loadContributionAdjustments } from "../../governance/api/governanceGateway";
import { loadIdentityAccounts, type IdentityAccountSummary } from "./identityApi";
import {
  isPlayableIdentityHandle,
  normalizePlayableIdentityHandle,
  normalizePlayerHandleKey
} from "../objects/identityHandlePolicy";

export interface ProfileIdentity {
  handle: string;
  displayName: string;
  skinId: string | null;
  typeLabel: string;
  sourceLabel: string;
}

export interface ProfileContribution {
  score: number;
  battleCount: number;
  adjustmentTotal: number;
  adjustmentCount: number | null;
  sourceLabel: string;
}

export interface ProfileRatingHistoryPoint {
  resultId: string;
  battleId: string;
  finishedAt: number;
  finishedAtLabel: string;
  ratingBefore: number;
  ratingAfter: number;
  ratingDelta: number;
}

export interface ProfileBattleHistoryItem extends ProfileRatingHistoryPoint {
  resultLabel: string;
  placement: number | null;
  placementLabel: string;
  score: number;
  performanceValue: number;
  performanceLabel: string;
  durationLabel: string;
  modeLabel: string;
  mapLabel: string;
  ratingLabel: string;
  ratingDeltaLabel: string;
}

export interface ProfileSummary {
  handle: string;
  displayName: string;
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
  identity: ProfileIdentity;
  contribution: ProfileContribution;
  ratingHistory: ProfileRatingHistoryPoint[];
  recentBattles: ProfileBattleHistoryItem[];
  matchCount: number;
  winCount: number;
  averageScore: number | null;
  averagePlacement: number | null;
  totalRatingDelta: number;
  performanceSummary: string;
  dataSources: string[];
}

let remoteProfileSummaryCache = new Map<string, ProfileSummary>();

/** 中文名：获取profile摘要（getProfileSummary）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function getProfileSummary(_handle: string): ProfileSummary | undefined {
  return undefined;
}

export async function loadProfileSummary(handle: string): Promise<ProfileSummary | undefined> {
  const requestedHandle = normalizePlayableIdentityHandle(handle);
  if (!requestedHandle) {
    return undefined;
  }

  const [remoteResults, remoteAccounts, remoteAdjustments] = await Promise.all([
    loadBattleResults({ handle: requestedHandle, limit: 50 }),
    loadIdentityAccounts(),
    loadContributionAdjustments()
  ]);
  if (remoteResults === null || remoteAccounts === null || remoteAdjustments === null) {
    return undefined;
  }

  const account = findIdentityAccount(requestedHandle, remoteAccounts);
  const summary = buildProfileSummary({
    requestedHandle,
    account,
    records: remoteResults,
    remoteAdjustments
  });

  writeRemoteProfileSummaryCache(summary);
  return summary;
}

function writeRemoteProfileSummaryCache(summary: ProfileSummary): void {
  remoteProfileSummaryCache = new Map(remoteProfileSummaryCache).set(normalizePlayerHandleKey(summary.handle), summary);
}

function buildProfileSummary(input: {
  requestedHandle: string;
  account: IdentityAccountSummary | undefined;
  records: BackendBattleResultRecord[];
  remoteAdjustments: NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>;
}): ProfileSummary {
  const profileRecords = filterBattleResultsByHandle(input.records, input.requestedHandle);
  const sortedRecent = sortBattleResultsByRecentness(profileRecords);
  const chronological = [...sortedRecent].reverse();
  const latest = sortedRecent[0];
  const resolvedHandle = input.account?.handle ?? latest?.handle ?? input.requestedHandle;
  const displayName = input.account?.displayName ?? latest?.displayName ?? resolvedHandle;
  const matchCount = sortedRecent.length;
  const winCount = sortedRecent.filter((record) => record.placement === 1).length;
  const winRate = matchCount > 0 ? `${Math.round((winCount / matchCount) * 100)}%` : null;
  const score = latest?.ratingAfter ?? (input.account ? 1200 : null);
  const totalRatingDelta = sortedRecent.reduce((total, record) => total + record.ratingDelta, 0);
  const recentBattles = sortedRecent.slice(0, 10).map(toProfileBattleHistoryItem);
  const contribution = buildProfileContribution(resolvedHandle, sortedRecent, input.remoteAdjustments);

  return {
    handle: resolvedHandle,
    displayName,
    score,
    winRate,
    title: score === null ? "未评级" : getRatingTitle(score),
    motto: buildMotto(matchCount, input.account),
    currentLoadout: latest?.currentLoadout ?? null,
    recentRecord: buildRecentRecord(matchCount, latest),
    recentMatches: recentBattles.slice(0, 5).map((battle) => ({
      title: battle.resultLabel,
      detail: `${battle.finishedAtLabel || "未知时间"} · ${battle.placementLabel} · 评分 ${battle.ratingLabel}`
    })),
    identity: buildProfileIdentity(resolvedHandle, displayName, input.account, latest),
    contribution,
    ratingHistory: chronological.map((record) => ({
      resultId: record.resultId,
      battleId: record.battleId,
      finishedAt: record.finishedAt,
      finishedAtLabel: record.finishedAtLabel,
      ratingBefore: record.ratingBefore,
      ratingAfter: record.ratingAfter,
      ratingDelta: record.ratingDelta
    })),
    recentBattles,
    matchCount,
    winCount,
    averageScore: average(sortedRecent.map((record) => record.score)),
    averagePlacement: average(sortedRecent.map((record) => record.placement).filter((placement): placement is number => placement !== null)),
    totalRatingDelta,
    performanceSummary: buildPerformanceSummary(sortedRecent),
    dataSources: buildDataSources(Boolean(input.account), input.remoteAdjustments !== null)
  };
}

function buildProfileContribution(
  handle: string,
  records: BackendBattleResultRecord[],
  remoteAdjustments: NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>
): ProfileContribution {
  const snapshot = buildContributionSnapshotForHandle(handle, records, remoteAdjustments);
  return {
    score: snapshot.score,
    battleCount: snapshot.battleCount,
    adjustmentTotal: snapshot.adjustmentTotal,
    adjustmentCount: snapshot.adjustmentCount,
    sourceLabel: "战局与管理员裁定"
  };
}

function buildProfileIdentity(
  handle: string,
  displayName: string,
  account: IdentityAccountSummary | undefined,
  latest: BackendBattleResultRecord | undefined
): ProfileIdentity {
  if (account) {
    return {
      handle,
      displayName,
      skinId: account.skinId || null,
      typeLabel: "已关联账号",
      sourceLabel: "账号档案"
    };
  }

  if (latest) {
    return {
      handle,
      displayName,
      skinId: null,
      typeLabel: "战局玩家",
      sourceLabel: "战局记录"
    };
  }

  return {
    handle,
    displayName,
    skinId: null,
    typeLabel: "尚未建档",
    sourceLabel: "暂无可用档案"
  };
}

function toProfileBattleHistoryItem(record: BackendBattleResultRecord): ProfileBattleHistoryItem {
  return {
    resultId: record.resultId,
    battleId: record.battleId,
    finishedAt: record.finishedAt,
    finishedAtLabel: record.finishedAtLabel,
    ratingBefore: record.ratingBefore,
    ratingAfter: record.ratingAfter,
    ratingDelta: record.ratingDelta,
    resultLabel: record.resultLabel || (record.placement === 1 ? "胜利" : "已结算"),
    placement: record.placement,
    placementLabel: formatPlacement(record.placement),
    score: record.score,
    performanceValue: record.score,
    performanceLabel: `${record.score} 分`,
    durationLabel: formatDuration(record.durationMs),
    modeLabel: record.modeLabel || "竞技模式",
    mapLabel: record.mapLabel || "默认地图",
    ratingLabel: `${record.ratingBefore} → ${record.ratingAfter}`,
    ratingDeltaLabel: formatSignedNumber(record.ratingDelta)
  };
}

function findIdentityAccount(requestedHandle: string, accounts: IdentityAccountSummary[]): IdentityAccountSummary | undefined {
  const key = normalizePlayerHandleKey(requestedHandle);
  return accounts.find((account) => isPlayableIdentityHandle(account.handle) && normalizePlayerHandleKey(account.handle) === key);
}

function filterBattleResultsByHandle(
  records: BackendBattleResultRecord[],
  requestedHandle: string
): BackendBattleResultRecord[] {
  const key = normalizePlayerHandleKey(requestedHandle);
  if (!key) {
    return [];
  }

  return records.filter((record) => isPlayableIdentityHandle(record.handle) && normalizePlayerHandleKey(record.handle) === key);
}

function sortBattleResultsByRecentness(records: BackendBattleResultRecord[]): BackendBattleResultRecord[] {
  return [...records].sort((left, right) => {
    if (right.finishedAt !== left.finishedAt) {
      return right.finishedAt - left.finishedAt;
    }

    if (right.ratingAfter !== left.ratingAfter) {
      return right.ratingAfter - left.ratingAfter;
    }

    return left.resultId.localeCompare(right.resultId);
  });
}

function buildMotto(matchCount: number, account: IdentityAccountSummary | undefined): string {
  if (matchCount > 0) {
    return "档案只统计已经入库的真实战局。";
  }

  if (account) {
    return "账号已建立，完成首局后会自动生成档案内容。";
  }

  return "暂未找到这个玩家的账号档案或战局记录。";
}

function buildRecentRecord(matchCount: number, latest: BackendBattleResultRecord | undefined): string {
  if (!latest) {
    return "暂无真实对局记录。";
  }

  return `已记录 ${matchCount} 场战局，最近一场 ${latest.resultLabel || "已结算"}，评分 ${latest.ratingBefore} → ${latest.ratingAfter}。`;
}

function buildPerformanceSummary(records: BackendBattleResultRecord[]): string {
  if (records.length === 0) {
    return "暂无真实战局，完成首局后才会生成近期表现。";
  }

  const recent = records.slice(0, 5);
  const recentWins = recent.filter((record) => record.placement === 1).length;
  const averageRecentScore = average(recent.map((record) => record.score));
  const recentRatingDelta = recent.reduce((total, record) => total + record.ratingDelta, 0);
  const bestPlacement = Math.min(...recent.map((record) => record.placement ?? Number.POSITIVE_INFINITY));
  const bestPlacementLabel = Number.isFinite(bestPlacement) ? `#${bestPlacement}` : "未记录";

  return `近 ${recent.length} 场：${recentWins} 胜，平均得分 ${averageRecentScore ?? 0}，最佳名次 ${bestPlacementLabel}，评分变化 ${formatSignedNumber(recentRatingDelta)}。`;
}

function buildDataSources(hasAccount: boolean, hasRemoteAdjustments: boolean): string[] {
  const sources: string[] = [];

  sources.push(hasAccount ? "账号档案已匹配" : "未匹配到账号档案");
  sources.push("战局记录已载入");
  sources.push(hasRemoteAdjustments ? "管理裁定已纳入" : "暂无可用裁定记录");
  return sources;
}

function average(values: number[]): number | null {
  if (values.length === 0) {
    return null;
  }

  const total = values.reduce((sum, value) => sum + value, 0);
  return Math.round(total / values.length);
}

function formatPlacement(placement: number | null): string {
  return placement === null ? "未记录" : `#${placement}`;
}

function formatDuration(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return "未记录";
  }

  const totalSeconds = Math.round(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function formatSignedNumber(value: number): string {
  return value > 0 ? `+${value}` : `${value}`;
}

function getRatingTitle(rating: number): string {
  if (rating >= 1500) {
    return "精英";
  }

  if (rating >= 1350) {
    return "冲击型";
  }

  return "竞技型";
}
