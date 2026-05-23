import { useSyncExternalStore, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../../../identity/api/authGateway";
import {
  getRatingEntries,
  isRemoteRatingSourceConfigured,
  loadRatingEntries,
  REMOTE_RATING_REFRESH_INTERVAL_MS
} from "../../api/ratingGateway";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";
import { useLobbyData } from "../../../../shared/ui/useLobbyData";

/** 中文名称：评分页。游戏职责：展示真实账号和战绩生成的 rating 列表。 */
export function RatingPage() {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const shouldRefreshRemoteRating = isRemoteRatingSourceConfigured();
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [authUser?.handle], {
    refreshIntervalMs: shouldRefreshRemoteRating ? REMOTE_RATING_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteRating
  });

  return (
    <ShellLayout title="评分榜单" subtitle="真实账号和战绩生成的 rating 列表。">
      {ratingEntries.length > 0 ? (
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-5">
          <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Rating</p>
                <h4 className="mt-1 text-lg font-semibold text-slate-950">Rating list</h4>
              </div>
              <span className="rounded-full border border-slate-200 px-3 py-1 text-xs font-semibold text-slate-600">
                {ratingEntries.length} 位玩家
              </span>
            </div>

            <section className="divide-y divide-slate-100" aria-label="评分榜单">
              <div className="grid grid-cols-[72px_minmax(150px,1fr)_90px_90px_120px_100px] items-center gap-3 bg-slate-50 px-5 py-3 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500 max-md:hidden">
                <span>#</span>
                <span>Handle</span>
                <span>场次</span>
                <span>胜率</span>
                <span>近期</span>
                <span>Rating</span>
              </div>
              {ratingEntries.map((entry) => (
                <div
                  key={entry.handle}
                  className="grid grid-cols-[64px_minmax(0,1fr)_auto] items-center gap-3 px-5 py-4 text-sm text-slate-700 md:grid-cols-[72px_minmax(150px,1fr)_90px_90px_120px_100px]"
                >
                  <span className="font-semibold text-slate-500">#{entry.rank}</span>
                  <div className="flex min-w-0 items-center gap-2">
                    <Link className="truncate font-semibold text-slate-950 hover:text-emerald-700" to={profilePath(entry.handle)}>
                      {entry.handle}
                    </Link>
                    <UserActionDot handle={entry.handle} />
                  </div>
                  <span className="hidden md:block">{entry.matchCount}</span>
                  <span className="hidden md:block">{entry.winRate}</span>
                  <span className="hidden font-medium text-slate-600 md:block">{formatRecentForm(entry.recentForm)}</span>
                  <strong className="justify-self-end text-lg font-semibold text-emerald-700 md:justify-self-start">{entry.score}</strong>
                  <div className="col-span-3 flex flex-wrap gap-2 text-xs text-slate-500 md:hidden">
                    <Badge>场次 {entry.matchCount}</Badge>
                    <Badge>胜率 {entry.winRate}</Badge>
                    <Badge>近期 {formatRecentForm(entry.recentForm)}</Badge>
                  </div>
                </div>
              ))}
            </section>
          </section>
        </div>
      ) : (
        <section className="mx-auto flex max-w-xl flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Rating</p>
          <h3 className="mt-2 text-xl font-semibold text-slate-950">当前没有评分记录</h3>
          <p className="mt-2 text-sm leading-6 text-slate-600">完成一局真实对局后，这里会出现榜单和排名。</p>
          <div className="mt-5">
            <Link className="inline-flex rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700" to="/battle?new=1">
              进入战斗
            </Link>
          </div>
        </section>
      )}
    </ShellLayout>
  );
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}

function formatRecentForm(recentForm: string): string {
  const normalized = recentForm.trim();
  return normalized && normalized !== "-" ? normalized : "无";
}

function Badge({ children }: { children: ReactNode }) {
  return <span className="rounded-full bg-slate-100 px-2 py-1 font-medium text-slate-600">{children}</span>;
}
