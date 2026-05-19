import { useEffect, useState, useSyncExternalStore } from "react";
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

interface RatingChartPoint {
  id: string;
  label: string;
  value: number;
}

/** 中文名：profilepage（ProfilePage）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
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
        <section className="detail-card empty-state empty-state--dense">
          <p className="eyebrow">档案加载中</p>
          <div className="user-handle-row">
            <Link to={profilePath(resolvedHandle)}>{resolvedHandle}</Link>
            <UserActionDot handle={resolvedHandle} sourceLabel="玩家档案" sourcePath={profilePath(resolvedHandle)} />
          </div>
          <p>这里只展示已经入库的真实对局，不补写不存在的历史。</p>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout
      title={`玩家档案 · ${profile.handle}`}
      subtitle="只展示已入库的真实战局、评分变化和近期表现。"
    >
      <div className="content-page content-page--profile profile-cf">
        <section className="profile-cf__layout">
          <aside className="profile-cf__sidebar" aria-label="玩家资料概览">
            <ProfileIdentityCard profile={profile} />
            <ProfileMetricGrid profile={profile} />

            <section className="profile-cf__panel profile-cf__panel--compact">
              <div className="profile-cf__section-title">
                <p className="eyebrow">近期状态</p>
                <h4>最近表现</h4>
              </div>
              <p className="profile-cf__note">{profile.performanceSummary}</p>
              <div className="profile-cf__mini-grid">
                <ProfileMiniStat label="总战局" value={String(profile.matchCount)} />
                <ProfileMiniStat label="胜场" value={String(profile.winCount)} />
                <ProfileMiniStat label="平均得分" value={profile.averageScore === null ? "暂无" : String(profile.averageScore)} />
                <ProfileMiniStat
                  label="平均名次"
                  value={profile.averagePlacement === null ? "暂无" : `#${profile.averagePlacement}`}
                />
              </div>
            </section>

            <section className="profile-cf__panel profile-cf__panel--compact">
              <div className="profile-cf__section-title">
                <p className="eyebrow">档案状态</p>
                <h4>同步来源</h4>
              </div>
              <ul className="profile-cf__sources">
                {profile.dataSources.map((source) => (
                  <li key={source}>{source}</li>
                ))}
              </ul>
            </section>
          </aside>

          <main className="profile-cf__main">
            <section className="profile-cf__panel profile-cf__panel--chart" aria-label="评分曲线">
              <div className="panel-header panel-header--dense">
                <div>
                  <p className="eyebrow">评分走势</p>
                  <h4>评分曲线</h4>
                </div>
                <span className="panel-header__meta">
                  {profile.ratingHistory.length > 0 ? `${profile.ratingHistory.length} 场` : "暂无足够对局"}
                </span>
              </div>
              <RatingCurve points={profile.ratingHistory} />
            </section>

            <section className="profile-cf__panel" aria-label="最近比赛历史">
              <div className="panel-header panel-header--dense">
                <div>
                  <p className="eyebrow">最近比赛</p>
                  <h4>比赛记录</h4>
                </div>
                <span className="panel-header__meta">{profile.recentBattles.length} 条</span>
              </div>
            <BattleHistoryTable battles={profile.recentBattles} profileHandle={profile.handle} />
            </section>
          </main>
        </section>
      </div>
    </ShellLayout>
  );
}

function ProfileIdentityCard({ profile }: { profile: ProfileSummary }) {
  return (
    <section className="profile-cf__identity profile-cf__panel">
      <div className="profile-cf__avatar" aria-hidden="true">
        {buildAvatarLabel(profile.handle)}
      </div>
      <div className="profile-cf__identity-copy">
        <p className="eyebrow">{profile.identity.typeLabel}</p>
        <div className="user-handle-row user-handle-row--large">
          <Link to={profilePath(profile.handle)}>{profile.handle}</Link>
          <UserActionDot handle={profile.handle} sourceLabel="玩家档案" sourcePath={profilePath(profile.handle)} />
        </div>
        <strong>{profile.displayName}</strong>
        <span>
          {profile.identity.sourceLabel}
          {profile.identity.skinId ? ` · skin ${profile.identity.skinId}` : ""}
        </span>
        <p>{profile.motto}</p>
      </div>
    </section>
  );
}

function ProfileMetricGrid({ profile }: { profile: ProfileSummary }) {
  return (
    <section className="profile-cf__metric-grid" aria-label="玩家核心指标">
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

function ProfileMetric({
  label,
  value,
  detail,
  tone
}: {
  label: string;
  value: string;
  detail: string;
  tone?: number;
}) {
  return (
    <article className="profile-cf__metric">
      <span>{label}</span>
      <strong className={toneClassName("profile-cf__metric-value", tone)}>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function ProfileMiniStat({ label, value }: { label: string; value: string }) {
  return (
    <article className="profile-cf__mini-stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function RatingCurve({ points }: { points: ProfileRatingHistoryPoint[] }) {
  const series = buildRatingChartSeries(points);

  if (series.length < 2) {
    return (
      <div className="profile-cf__empty-graph">
        <strong>暂无足够对局</strong>
        <span>完成至少一场真实对局后，系统才会生成评分曲线。</span>
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
    <div className="profile-cf__chart-wrap">
      <svg className="profile-cf__chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="评分历史图">
        <defs>
          <linearGradient id="profileRatingArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="rgba(255, 214, 112, 0.28)" />
            <stop offset="100%" stopColor="rgba(255, 214, 112, 0)" />
          </linearGradient>
        </defs>
        <line className="profile-cf__chart-grid" x1={paddingX} x2={width - paddingX} y1={paddingTop} y2={paddingTop} />
        <line
          className="profile-cf__chart-grid"
          x1={paddingX}
          x2={width - paddingX}
          y1={height - paddingBottom}
          y2={height - paddingBottom}
        />
        <path className="profile-cf__chart-area" d={areaPath} />
        <path className="profile-cf__chart-line" d={linePath} />
        {series.map((point, index) => (
          <g key={point.id}>
            <circle className="profile-cf__chart-dot" cx={xFor(index)} cy={yFor(point.value)} r={4.2} />
            {index === 0 || index === series.length - 1 ? (
              <text className="profile-cf__chart-value" x={xFor(index)} y={yFor(point.value) - 10} textAnchor={index === 0 ? "start" : "end"}>
                {point.value}
              </text>
            ) : null}
          </g>
        ))}
        <text className="profile-cf__chart-axis" x={paddingX} y={height - 10}>
          {series[0]?.label}
        </text>
        <text className="profile-cf__chart-axis" x={width - paddingX} y={height - 10} textAnchor="end">
          {series[series.length - 1]?.label}
        </text>
      </svg>
    </div>
  );
}

function BattleHistoryTable({ battles, profileHandle }: { battles: ProfileBattleHistoryItem[]; profileHandle: string }) {
  if (battles.length === 0) {
    return (
      <div className="profile-cf__empty-history">
        <strong>暂无足够对局</strong>
        <span>没有入库战绩时，不会补写最近比赛、评分变化或表现值。</span>
      </div>
    );
  }

  return (
    <div className="profile-cf__history-table">
      <div className="profile-cf__history-head">
        <span>时间</span>
        <span>结果</span>
        <span>评分</span>
        <span>表现</span>
      </div>
      {battles.map((battle) => (
        <article className="profile-cf__history-row" key={battle.resultId}>
          <span className="profile-cf__history-time">{battle.finishedAtLabel || "未知时间"}</span>
          <span className="profile-cf__history-result">
            <strong>{battle.resultLabel}</strong>
            <small>
              {battle.placementLabel} · {battle.modeLabel}
            </small>
          </span>
          <span className="profile-cf__history-rating">
            <strong>{battle.ratingLabel}</strong>
            <small className={toneClassName("profile-cf__delta", battle.ratingDelta)}>{battle.ratingDeltaLabel}</small>
          </span>
          <span className="profile-cf__history-performance">
            <strong>{battle.performanceLabel}</strong>
            <small>
              {battle.durationLabel} · {battle.mapLabel}
            </small>
            <Link to={replayPath(battle.battleId, profileHandle)}>查看回放</Link>
          </span>
        </article>
      ))}
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
  return `${profile.contribution.battleCount} 场战局 · ${formatSignedNumber(profile.contribution.adjustmentTotal)} 管理员裁定 · ${adjustmentCount}`;
}

function toneClassName(baseClassName: string, value: number | undefined): string {
  if (typeof value === "undefined" || value === 0) {
    return baseClassName;
  }

  return `${baseClassName} ${baseClassName}--${value > 0 ? "positive" : "negative"}`;
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
