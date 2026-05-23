import { useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../../../identity/api/authGateway";
import {
  getContributionEntries,
  isRemoteContributionSourceConfigured,
  loadContributionEntries,
  REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS
} from "../../api/contributionGateway";
import { CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT } from "../../api/governanceGateway";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";
import { useLobbyData } from "../../../../shared/ui/useLobbyData";

/** 中文名称：贡献页。游戏职责：展示真实账号、战报和治理调整生成的 contribution 列表。 */
export function ContributionPage() {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const shouldRefreshRemoteContribution = isRemoteContributionSourceConfigured();
  const entries = useLobbyData(() => getContributionEntries(), loadContributionEntries, [authUser?.handle], {
    refreshIntervalMs: shouldRefreshRemoteContribution ? REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteContribution,
    refreshEvents: [CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT]
  });

  return (
    <ShellLayout title="贡献榜单" subtitle="真实账号、战报和治理调整生成的 contribution 列表。">
      {entries.length > 0 ? (
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-5">
          <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Contribution</p>
                <h4 className="mt-1 text-lg font-semibold text-slate-950">Contribution list</h4>
              </div>
              <span className="rounded-full border border-slate-200 px-3 py-1 text-xs font-semibold text-slate-600">
                {entries.length} 位玩家
              </span>
            </div>

            <section className="divide-y divide-slate-100" aria-label="贡献榜单">
              <div className="grid grid-cols-[72px_minmax(150px,1fr)_120px_120px] items-center gap-3 bg-slate-50 px-5 py-3 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500 max-md:hidden">
                <span>#</span>
                <span>Handle</span>
                <span>状态</span>
                <span>Contribution</span>
              </div>
              {entries.map((entry) => (
                <div
                  key={entry.handle}
                  className="grid grid-cols-[64px_minmax(0,1fr)_auto] items-center gap-3 px-5 py-4 text-sm text-slate-700 md:grid-cols-[72px_minmax(150px,1fr)_120px_120px]"
                >
                  <span className="font-semibold text-slate-500">#{entry.rank}</span>
                  <div className="flex min-w-0 items-center gap-2">
                    <Link className="truncate font-semibold text-slate-950 hover:text-emerald-700" to={profilePath(entry.handle)}>
                      {entry.handle}
                    </Link>
                    <UserActionDot handle={entry.handle} />
                  </div>
                  <span className="hidden md:block">{formatContributionStatus(entry.totalActions)}</span>
                  <strong className="justify-self-end text-lg font-semibold text-emerald-700 md:justify-self-start">{entry.score}</strong>
                  <span className="col-span-3 w-fit rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600 md:hidden">
                    {formatContributionStatus(entry.totalActions)}
                  </span>
                </div>
              ))}
            </section>
          </section>
        </div>
      ) : (
        <section className="mx-auto flex max-w-xl flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Contribution</p>
          <h3 className="mt-2 text-xl font-semibold text-slate-950">当前没有贡献记录</h3>
          <p className="mt-2 text-sm leading-6 text-slate-600">完成对局、发帖或回复后，这里会出现真实贡献数据。</p>
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

function formatContributionStatus(totalActions: number): string {
  return totalActions > 0 ? "已记录" : "暂无";
}
