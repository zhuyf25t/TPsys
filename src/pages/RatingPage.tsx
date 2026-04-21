import { useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
import { getRatingEntries, loadRatingEntries } from "../features/rating/ratingGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";
import { UserActionDot } from "../shared/ui/UserActionDot";
import { useLobbyData } from "../shared/ui/useLobbyData";

export function RatingPage() {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [authUser?.handle]);

  const leader = ratingEntries[0];
  const topRows = ratingEntries.slice(0, 10);

  return (
    <ShellLayout title="评分榜单" subtitle="只展示真实对局生成的榜单，不补造数据。">
      {leader ? (
        <div className="content-page content-page--ranking">
          <section className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Rating</p>
                <h4>评分排行</h4>
              </div>
              <span className="panel-header__meta">{ratingEntries.length} 位玩家</span>
            </div>

            <div className="content-page__summary-strip">
              <article className="info-tile">
                <span>榜首</span>
                <div className="user-handle-row">
                  <Link to={profilePath(leader.handle)}>{leader.handle}</Link>
                  <UserActionDot handle={leader.handle} />
                </div>
                <small>{leader.title}</small>
              </article>
              <article className="info-tile">
                <span>评分</span>
                <strong>{leader.score}</strong>
                <small>{leader.highlight}</small>
              </article>
              <article className="info-tile">
                <span>战绩</span>
                <strong>{leader.matchCount}</strong>
                <small>
                  {leader.winRate} / {leader.recentForm || "—"}
                </small>
              </article>
            </div>

            <section className="cf-ranking-table cf-ranking-table--compact" aria-label="评分榜单">
              <div className="cf-ranking-table__header">
                <span>#</span>
                <span>玩家</span>
                <span>近期</span>
                <span>评分</span>
              </div>
              {topRows.map((entry) => (
                <div key={entry.handle} className="cf-ranking-table__row">
                  <span className="cf-ranking-table__rank">#{entry.rank}</span>
                  <div className="cf-ranking-table__meta">
                    <div className="user-handle-row">
                      <Link to={profilePath(entry.handle)}>{entry.handle}</Link>
                      <UserActionDot handle={entry.handle} />
                    </div>
                    <small>
                      {entry.title} · {entry.matchCount} 局 · {entry.winRate}
                    </small>
                  </div>
                  <span className="cf-ranking-table__form">{entry.recentForm || "—"}</span>
                  <strong className="cf-ranking-table__score">{entry.score}</strong>
                </div>
              ))}
            </section>
          </section>
        </div>
      ) : (
        <section className="detail-card empty-state empty-state--dense">
          <p className="eyebrow">Rating</p>
          <h3>当前没有评分记录</h3>
          <p>完成一局真实对局后，这里才会出现榜单和排名。</p>
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
