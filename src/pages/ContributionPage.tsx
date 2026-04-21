import { useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
import {
  getContributionEntries,
  getContributionSummary,
  loadContributionEntries,
  loadContributionSummary
} from "../features/contribution/contributionGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";
import { UserActionDot } from "../shared/ui/UserActionDot";
import { useLobbyData } from "../shared/ui/useLobbyData";

export function ContributionPage() {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const entries = useLobbyData(() => getContributionEntries(), loadContributionEntries, [authUser?.handle]);
  const summary = useLobbyData(() => getContributionSummary(), loadContributionSummary, [authUser?.handle]);

  return (
    <ShellLayout title="贡献榜单" subtitle="只展示真实战报、发帖和回复累积的贡献记录。">
      {entries.length > 0 ? (
        <div className="content-page content-page--contribution">
          <section className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Contribution</p>
                <h4>贡献排行</h4>
              </div>
              <span className="panel-header__meta">{entries.length} 位玩家</span>
            </div>

            <section className="cf-ranking-table cf-ranking-table--compact cf-ranking-table--contribution" aria-label="贡献榜单">
              <div className="cf-ranking-table__header">
                <span>#</span>
                <span>玩家</span>
                <span>贡献</span>
              </div>
              {entries.slice(0, 10).map((entry) => (
                <div key={entry.handle} className="cf-ranking-table__row">
                  <span className="cf-ranking-table__rank">#{entry.rank}</span>
                  <div className="cf-ranking-table__meta">
                    <div className="user-handle-row">
                      <Link to={profilePath(entry.handle)}>{entry.handle}</Link>
                      <UserActionDot handle={entry.handle} />
                    </div>
                    <small>真实贡献 · {entry.totalActions} 项</small>
                  </div>
                  <strong className="cf-ranking-table__score">{entry.totalActions}</strong>
                </div>
              ))}
            </section>

            {summary ? (
              <div className="content-page__summary-strip content-page__summary-strip--contribution">
                <article className="info-tile">
                  <span>当前玩家</span>
                  <div className="user-handle-row">
                    <Link to={profilePath(summary.handle)}>{summary.handle}</Link>
                    <UserActionDot handle={summary.handle} />
                  </div>
                  <small>{summary.title}</small>
                </article>
                <article className="info-tile">
                  <span>最近活跃</span>
                  <strong>{summary.latestActivityLabel}</strong>
                  <small>{summary.detail}</small>
                </article>
              </div>
            ) : null}
          </section>
        </div>
      ) : (
        <section className="detail-card empty-state empty-state--dense">
          <p className="eyebrow">Contribution</p>
          <h3>当前没有贡献记录</h3>
          <p>完成对局、发帖或回复后，这里才会出现真实贡献数据。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle">
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
