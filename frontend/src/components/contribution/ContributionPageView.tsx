import { Link } from "react-router-dom";
import type { ContributionEntry } from "../../api/governance/contributionGateway";
import { ShellLayout } from "../../shared/ui/ShellLayout";
import { UserActionDot } from "../user-action-dot/UserActionDot";

interface ContributionPageViewProps {
  entries: ContributionEntry[];
}

export function ContributionPageView({ entries }: ContributionPageViewProps) {
  return (
    <ShellLayout title="贡献榜单" subtitle="真实账户、战报和治理调整生成的 contribution 列表。">
      {entries.length > 0 ? (
        <div className="content-page content-page--contribution">
          <section className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Contribution</p>
                <h4>Contribution list</h4>
              </div>
              <span className="panel-header__meta">{entries.length} 位玩家</span>
            </div>

            <section className="cf-ranking-table cf-ranking-table--compact cf-ranking-table--contribution" aria-label="贡献榜单">
              <div className="cf-ranking-table__header">
                <span>#</span>
                <span>Handle</span>
                <span className="cf-ranking-table__cell--status">状态</span>
                <span>Contribution</span>
              </div>
              {entries.map((entry) => (
                <div key={entry.handle} className="cf-ranking-table__row">
                  <span className="cf-ranking-table__rank">#{entry.rank}</span>
                  <div className="cf-ranking-table__handle user-handle-row">
                    <Link to={profilePath(entry.handle)}>{entry.handle}</Link>
                    <UserActionDot handle={entry.handle} />
                  </div>
                  <span className="cf-ranking-table__status cf-ranking-table__cell--status">{formatContributionStatus(entry.totalActions)}</span>
                  <strong className="cf-ranking-table__score">{entry.score}</strong>
                </div>
              ))}
            </section>
          </section>
        </div>
      ) : (
        <section className="detail-card empty-state empty-state--dense">
          <p className="eyebrow">Contribution</p>
          <h3>当前没有贡献记录</h3>
          <p>完成对局、发帖或回复后，这里才会出现真实贡献数据。</p>
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

function formatContributionStatus(totalActions: number): string {
  return totalActions > 0 ? "已记录" : "暂无";
}
