import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { getCurrentAuthHandle, getCurrentAuthUser, subscribeAuthState } from "../../api/authGateway";
import {
  getProfileSummary,
  loadProfileSummary,
  type ProfileBattleHistoryItem,
  type ProfileRatingHistoryPoint,
  type ProfileSummary
} from "../../api/profileGateway";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";
import { cn } from "../../../../shared/ui/classNames";

interface RatingChartPoint {
  id: string;
  label: string;
  value: number;
}

/** 中文名称：玩家档案页。游戏职责：展示玩家身份、真实战局、评分变化和近期表现。 */
export function ProfilePage() {
  const { handle } = useParams<{ handle: string }>();
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const resolvedHandle = handle ?? authUser?.handle ?? getCurrentAuthHandle();
  const [profile, setProfile] = useState<ProfileSummary | undefined>(() => getProfileSummary(resolvedHandle));

  useEffect(() => {
    let cancelled = false;

    void loadProfileSummary(resolvedHandle).then((summary) => {
      if (!cancelled) {
        setProfile(summary);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [resolvedHandle]);

  if (!profile) {
    return (
      <ShellLayout title="玩家档案" subtitle="正在读取真实战局、评分变化和档案状态。">
        <section className="mx-auto flex max-w-xl flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">档案加载中</p>
          <div className="mt-3 flex items-center gap-2">
            <Link className="font-semibold text-slate-950 hover:text-emerald-700" to={profilePath(resolvedHandle)}>
              {resolvedHandle}
            </Link>
            <UserActionDot handle={resolvedHandle} sourceLabel="玩家档案" sourcePath={profilePath(resolvedHandle)} />
          </div>
          <p className="mt-3 text-sm leading-6 text-slate-600">这里只展示已经入库的真实对局，不补写不存在的历史。</p>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title={`玩家档案 | ${profile.handle}`} subtitle="只展示已入库的真实战局、评分变化和近期表现。">
      <div className="mx-auto w-full max-w-7xl">
        <section className="grid gap-5 lg:grid-cols-[340px_minmax(0,1fr)]" aria-label="玩家资料">
          <aside className="flex flex-col gap-5" aria-label="玩家资料概览">
            <ProfileIdentityCard profile={profile} />
            <ProfileMetricGrid profile={profile} />

            <Panel>
              <SectionTitle eyebrow="近期状态" title="最近表现" />
              <p className="mt-3 text-sm leading-6 text-slate-600">{profile.performanceSummary}</p>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <ProfileMiniStat label="总战局" value={String(profile.matchCount)} />
                <ProfileMiniStat label="胜场" value={String(profile.winCount)} />
                <ProfileMiniStat label="平均得分" value={profile.averageScore === null ? "暂无" : String(profile.averageScore)} />
                <ProfileMiniStat label="平均名次" value={profile.averagePlacement === null ? "暂无" : `#${profile.averagePlacement}`} />
              </div>
            </Panel>

            <Panel>
              <SectionTitle eyebrow="档案状态" title="同步来源" />
              <ul className="mt-4 flex flex-wrap gap-2">
                {profile.dataSources.map((source) => (
                  <li key={source} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
                    {source}
                  </li>
                ))}
              </ul>
            </Panel>
          </aside>

          <main className="flex min-w-0 flex-col gap-5">
            <Panel ariaLabel="评分曲线">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <SectionTitle eyebrow="评分走势" title="评分曲线" />
                <span className="rounded-full border border-slate-200 px-3 py-1 text-xs font-semibold text-slate-600">
                  {profile.ratingHistory.length > 0 ? `${profile.ratingHistory.length} 场` : "暂无足够对局"}
                </span>
              </div>
              <RatingCurve points={profile.ratingHistory} />
            </Panel>

            <Panel ariaLabel="最近比赛历史">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <SectionTitle eyebrow="最近比赛" title="比赛记录" />
                <span className="rounded-full border border-slate-200 px-3 py-1 text-xs font-semibold text-slate-600">
                  {profile.recentBattles.length} 条
                </span>
              </div>
              <BattleHistoryTable battles={profile.recentBattles} profileHandle={profile.handle} />
            </Panel>
          </main>
        </section>
      </div>
    </ShellLayout>
  );
}

function ProfileIdentityCard({ profile }: { profile: ProfileSummary }) {
  return (
    <Panel>
      <div className="flex items-start gap-4">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-emerald-600 text-xl font-bold text-white shadow-sm" aria-hidden="true">
          {buildAvatarLabel(profile.handle)}
        </div>
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">{profile.identity.typeLabel}</p>
          <div className="mt-2 flex min-w-0 items-center gap-2">
            <Link className="truncate text-xl font-semibold text-slate-950 hover:text-emerald-700" to={profilePath(profile.handle)}>
              {profile.handle}
            </Link>
            <UserActionDot handle={profile.handle} sourceLabel="玩家档案" sourcePath={profilePath(profile.handle)} />
          </div>
          <strong className="mt-1 block text-sm text-slate-700">{profile.displayName}</strong>
          <span className="mt-1 block text-xs text-slate-500">
            {profile.identity.sourceLabel}
            {profile.identity.skinId ? ` | skin ${profile.identity.skinId}` : ""}
          </span>
          <p className="mt-3 text-sm leading-6 text-slate-600">{profile.motto}</p>
        </div>
      </div>
    </Panel>
  );
}

function ProfileMetricGrid({ profile }: { profile: ProfileSummary }) {
  return (
    <section className="grid grid-cols-2 gap-3" aria-label="玩家核心指标">
      <ProfileMetric label="当前评分" value={profile.score === null ? "暂无" : String(profile.score)} detail={profile.title} />
      <ProfileMetric label="战局贡献" value={String(profile.contribution.score)} detail={formatContributionDetail(profile)} />
      <ProfileMetric label="胜率" value={profile.winRate ?? "暂无"} detail={`${profile.matchCount} 场已记录`} />
      <ProfileMetric
        label="总评分变化"
        value={formatSignedNumber(profile.totalRatingDelta)}
        detail={profile.matchCount > 0 ? "来自已入库对局" : "暂无对局"}
        tone={profile.totalRatingDelta}
      />
    </section>
  );
}

function ProfileMetric({ label, value, detail, tone }: { label: string; value: string; detail: string; tone?: number }) {
  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <strong className={cn("mt-2 block text-2xl font-semibold", toneTextClass(tone))}>{value}</strong>
      <small className="mt-1 block text-xs leading-5 text-slate-500">{detail}</small>
    </article>
  );
}

function ProfileMiniStat({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded-md bg-slate-50 p-3">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <strong className="mt-1 block text-base font-semibold text-slate-950">{value}</strong>
    </article>
  );
}

function RatingCurve({ points }: { points: ProfileRatingHistoryPoint[] }) {
  const series = buildRatingChartSeries(points);

  if (series.length < 2) {
    return (
      <div className="mt-4 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-600">
        <strong className="block text-slate-950">暂无足够对局</strong>
        <span className="mt-1 block">完成至少一场真实对局后，系统才会生成评分曲线。</span>
      </div>
    );
  }

  const width = 720;
  const height = 220;
  const paddingX = 34;
  const paddingTop = 22;
  const paddingBottom = 34;
  const values = series.map((point) => point.value);
  const rawMin = Math.min(...values);
  const rawMax = Math.max(...values);
  const rawRange = Math.max(1, rawMax - rawMin);
  const min = rawMin - Math.max(10, Math.round(rawRange * 0.18));
  const max = rawMax + Math.max(10, Math.round(rawRange * 0.18));
  const range = Math.max(1, max - min);
  const plotWidth = width - paddingX * 2;
  const plotHeight = height - paddingTop - paddingBottom;
  const xFor = (index: number): number => paddingX + (series.length === 1 ? 0 : (plotWidth * index) / (series.length - 1));
  const yFor = (value: number): number => paddingTop + plotHeight - ((value - min) / range) * plotHeight;
  const linePath = series.map((point, index) => `${index === 0 ? "M" : "L"} ${xFor(index).toFixed(2)} ${yFor(point.value).toFixed(2)}`).join(" ");
  const areaPath = `${linePath} L ${xFor(series.length - 1).toFixed(2)} ${height - paddingBottom} L ${paddingX} ${height - paddingBottom} Z`;

  return (
    <div className="mt-4 overflow-hidden rounded-lg border border-slate-200 bg-slate-50 p-3">
      <svg className="h-auto w-full" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="评分历史图">
        <defs>
          <linearGradient id="profileRatingArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="rgba(5, 150, 105, 0.22)" />
            <stop offset="100%" stopColor="rgba(5, 150, 105, 0)" />
          </linearGradient>
        </defs>
        <line x1={paddingX} x2={width - paddingX} y1={paddingTop} y2={paddingTop} stroke="#e2e8f0" strokeWidth="1" />
        <line x1={paddingX} x2={width - paddingX} y1={height - paddingBottom} y2={height - paddingBottom} stroke="#e2e8f0" strokeWidth="1" />
        <path d={areaPath} fill="url(#profileRatingArea)" />
        <path d={linePath} fill="none" stroke="#059669" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" />
        {series.map((point, index) => (
          <g key={point.id}>
            <circle cx={xFor(index)} cy={yFor(point.value)} r={4.2} fill="#059669" stroke="#ffffff" strokeWidth="2" />
            {index === 0 || index === series.length - 1 ? (
              <text x={xFor(index)} y={yFor(point.value) - 10} textAnchor={index === 0 ? "start" : "end"} fill="#334155" fontSize="12" fontWeight="700">
                {point.value}
              </text>
            ) : null}
          </g>
        ))}
        <text x={paddingX} y={height - 10} fill="#64748b" fontSize="12">
          {series[0]?.label}
        </text>
        <text x={width - paddingX} y={height - 10} textAnchor="end" fill="#64748b" fontSize="12">
          {series[series.length - 1]?.label}
        </text>
      </svg>
    </div>
  );
}

function BattleHistoryTable({ battles, profileHandle }: { battles: ProfileBattleHistoryItem[]; profileHandle: string }) {
  if (battles.length === 0) {
    return (
      <div className="mt-4 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-600">
        <strong className="block text-slate-950">暂无足够对局</strong>
        <span className="mt-1 block">没有入库战绩时，不会补写最近比赛、评分变化或表现值。</span>
      </div>
    );
  }

  return (
    <div className="mt-4 overflow-hidden rounded-lg border border-slate-200">
      <div className="grid grid-cols-[140px_1fr_120px_1fr] gap-3 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500 max-md:hidden">
        <span>时间</span>
        <span>结果</span>
        <span>评分</span>
        <span>表现</span>
      </div>
      <div className="divide-y divide-slate-100">
        {battles.map((battle) => (
          <article key={battle.resultId} className="grid gap-3 px-4 py-4 text-sm text-slate-700 md:grid-cols-[140px_1fr_120px_1fr]">
            <span className="font-medium text-slate-500">{battle.finishedAtLabel || "未知时间"}</span>
            <span>
              <strong className="block text-slate-950">{battle.resultLabel}</strong>
              <small className="text-slate-500">
                {battle.placementLabel} | {battle.modeLabel}
              </small>
            </span>
            <span>
              <strong className="block text-slate-950">{battle.ratingLabel}</strong>
              <small className={cn("font-semibold", toneTextClass(battle.ratingDelta))}>{battle.ratingDeltaLabel}</small>
            </span>
            <span>
              <strong className="block text-slate-950">{battle.performanceLabel}</strong>
              <small className="block text-slate-500">
                {battle.durationLabel} | {battle.mapLabel}
              </small>
              <Link className="mt-1 inline-flex text-xs font-semibold text-emerald-700 hover:text-emerald-800" to={replayPath(battle.battleId, profileHandle)}>
                查看回放
              </Link>
            </span>
          </article>
        ))}
      </div>
    </div>
  );
}

function Panel({ children, ariaLabel }: { children: ReactNode; ariaLabel?: string }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-label={ariaLabel}>
      {children}
    </section>
  );
}

function SectionTitle({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">{eyebrow}</p>
      <h4 className="mt-1 text-lg font-semibold text-slate-950">{title}</h4>
    </div>
  );
}

function replayPath(battleId: string, handle: string): string {
  return `/replay/${encodeURIComponent(battleId)}?handle=${encodeURIComponent(handle)}`;
}

function buildRatingChartSeries(points: ProfileRatingHistoryPoint[]): RatingChartPoint[] {
  if (points.length === 0) {
    return [];
  }

  const chronological = [...points].sort((left, right) => left.finishedAt - right.finishedAt);
  const first = chronological[0];

  return [
    {
      id: `${first.resultId}:start`,
      label: "起点",
      value: first.ratingBefore
    },
    ...chronological.map((point) => ({
      id: point.resultId,
      label: point.finishedAtLabel || "最近",
      value: point.ratingAfter
    }))
  ];
}

function formatContributionDetail(profile: ProfileSummary): string {
  const adjustmentCount = profile.contribution.adjustmentCount === null ? "已缓存" : `${profile.contribution.adjustmentCount} 次`;
  return `${profile.contribution.battleCount} 场战局 | ${formatSignedNumber(profile.contribution.adjustmentTotal)} 管理员裁定 | ${adjustmentCount}`;
}

function toneTextClass(value: number | undefined): string {
  if (typeof value === "undefined" || value === 0) {
    return "text-slate-950";
  }

  return value > 0 ? "text-emerald-700" : "text-rose-700";
}

function formatSignedNumber(value: number): string {
  return value > 0 ? `+${value}` : `${value}`;
}

function buildAvatarLabel(handle: string): string {
  const letters = handle
    .trim()
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join("");

  return (letters || handle.slice(0, 2) || "?").slice(0, 2).toUpperCase();
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}
