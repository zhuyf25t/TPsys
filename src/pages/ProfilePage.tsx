import { useSyncExternalStore } from "react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCurrentAuthHandle, getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
import { getProfileSummary, loadProfileSummary } from "../features/profile/profileGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";
import { UserActionDot } from "../shared/ui/UserActionDot";

export function ProfilePage() {
  const { handle } = useParams<{ handle: string }>();
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const resolvedHandle = handle ?? authUser?.handle ?? getCurrentAuthHandle();
  const [profile, setProfile] = useState(() => getProfileSummary(resolvedHandle));

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
      <ShellLayout title="玩家档案" subtitle="当前玩家还没有足够的真实战绩可展示。">
        <section className="detail-card empty-state empty-state--dense">
          <p className="eyebrow">Profile</p>
          <div className="user-handle-row">
            <Link to={profilePath(resolvedHandle)}>{resolvedHandle}</Link>
            <UserActionDot handle={resolvedHandle} />
          </div>
          <p>只有打过真实对局的玩家，档案页才会开始积累可读内容。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle">
              进入战斗
            </Link>
            <Link className="button-link" to="/rating">
              查看评分榜
            </Link>
          </div>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title={`玩家档案 · ${profile.handle}`} subtitle="档案页只展示真实战局、真实结果和真实配置。">
      <div className="content-page content-page--profile">
        <section className="profile-archive">
          <article className="profile-archive__hero">
            <div className="profile-archive__identity">
              <div className="profile-archive__avatar">{profile.handle.slice(0, 2).toUpperCase()}</div>
              <div className="profile-archive__copy">
                <p className="eyebrow">{profile.title}</p>
                <div className="user-handle-row user-handle-row--large">
                  <Link to={profilePath(profile.handle)}>{profile.handle}</Link>
                  <UserActionDot handle={profile.handle} />
                </div>
                <p>{profile.motto}</p>
                <div className="pill-row content-page__chips">
                  <span className="pill">当前配置 {profile.currentLoadout ?? "暂无记录"}</span>
                  <span className="pill">评分 {profile.score ?? "暂无"}</span>
                  <span className="pill">胜率 {profile.winRate ?? "暂无"}</span>
                </div>
              </div>
            </div>

            <div className="profile-archive__stats">
              <article>
                <span>当前评分</span>
                <strong>{profile.score ?? "暂无"}</strong>
              </article>
              <article>
                <span>胜率</span>
                <strong>{profile.winRate ?? "暂无"}</strong>
              </article>
              <article>
                <span>当前配置</span>
                <strong>{profile.currentLoadout ?? "完成一局后出现"}</strong>
              </article>
              <article>
                <span>档案状态</span>
                <strong>{profile.recentMatches.length > 0 ? "已积累战绩" : "等待首局"}</strong>
              </article>
            </div>
          </article>
        </section>

        <section className="content-page__grid content-page__grid--profile">
          <main className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Recent Matches</p>
                <h4>最近比赛</h4>
              </div>
              <span className="panel-header__meta">{profile.recentMatches.length} 条</span>
            </div>

            {profile.recentMatches.length === 0 ? (
              <section className="detail-card empty-state empty-state--dense">
                <h3>还没有最近对局</h3>
                <p>先完成一局，这里的最近比赛和总结才会开始积累。</p>
              </section>
            ) : (
              <div className="profile-timeline">
                {profile.recentMatches.map((match) => (
                  <article className="profile-timeline__item" key={`${match.title}-${match.detail}`}>
                    <strong>{match.title}</strong>
                    <span>{match.detail}</span>
                  </article>
                ))}
              </div>
            )}
          </main>

          <aside className="content-page__panel content-page__panel--side">
            <article className="summary-stack">
              <div className="summary-stack__header">
                <p className="eyebrow">Snapshot</p>
                <h4>当前状态</h4>
              </div>
              <div className="summary-stack__body">
                <p>{profile.recentRecord}</p>
                <p>
                  {profile.currentLoadout
                    ? "这份档案来自真实战局，当前配置和最近比赛会持续累积。"
                    : "完成首局后，这里会自动出现当前配置和档案摘要。"}
                </p>
              </div>
              <div className="cta-row">
                <Link className="button-link button-link--primary" to="/battle">
                  进入战斗
                </Link>
                <Link className="button-link" to="/replay">
                  查看回放
                </Link>
              </div>
            </article>

            <article className="summary-stack">
              <div className="summary-stack__header">
                <p className="eyebrow">History</p>
                <h4>历史 / 趋势预留</h4>
              </div>
              <p className="summary-stack__empty">
                当前还没有足够多的真实对局生成曲线。这里先保留给后续历史与趋势。
              </p>
            </article>
          </aside>
        </section>
      </div>
    </ShellLayout>
  );
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}
