import { Link } from "react-router-dom";
import type { RatingEntry } from "../../../apis/governance/ratingGateway";
import { ShellLayout } from "../../../components/ui/ShellLayout";
import { UserActionDot } from "../../shared/components/user-action-dot/UserActionDot";

interface RatingPageViewProps {
  ratingEntries: RatingEntry[];
}

export function RatingPageView({ ratingEntries }: RatingPageViewProps) {
  return (
    <ShellLayout title="评分榜单" subtitle="真实账户和战绩生成的 rating 列表。">
      {ratingEntries.length > 0 ? (
        <div className="content-page content-page--ranking">
          <section className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Rating</p>
                <h4>Rating list</h4>
              </div>
              <span className="panel-header__meta">{ratingEntries.length} 位玩家</span>
            </div>

            <section className="cf-ranking-table cf-ranking-table--compact cf-ranking-table--rating" aria-label="评分榜单">
              <div className="cf-ranking-table__header">
                <span>#</span>
                <span>Handle</span>
                <span className="cf-ranking-table__cell--matches">场次</span>
                <span className="cf-ranking-table__cell--win-rate">胜率</span>
                <span>近期</span>
                <span>Rating</span>
              </div>
              {ratingEntries.map((entry) => (
                <div key={entry.handle} className="cf-ranking-table__row">
                  <span className="cf-ranking-table__rank">#{entry.rank}</span>
                  <div className="cf-ranking-table__handle user-handle-row">
                    <Link to={profilePath(entry.handle)}>{entry.handle}</Link>
                    <UserActionDot handle={entry.handle} />
                  </div>
                  <span className="cf-ranking-table__cell--matches">{entry.matchCount}</span>
                  <span className="cf-ranking-table__cell--win-rate">{entry.winRate}</span>
                  <span className="cf-ranking-table__form">{formatRecentForm(entry.recentForm)}</span>
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
            <Link className="button-link button-link--primary" to="/battle?new=1">
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
  return normalized && normalized !== "-" ? normalized : "—";
}
