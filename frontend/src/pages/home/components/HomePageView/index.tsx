import { AuthOverlay } from "../../../shared/components/auth/AuthOverlay";
import type { HomeLeaderboardEntry, HomePageState } from "../../hooks/useHomePage";
import { LobbyShell } from "../../../../components/ui/LobbyShell";

/** 中文名称：首页视图。游戏职责：渲染大厅壳、排行榜和认证弹窗。 */
export function HomePageView({
  authMode,
  battleModeDetail,
  closeAuthOverlay,
  completeAuth,
  contributionLeaderboard,
  currentLoadoutLabel,
  currentRatingLabel,
  loadoutPrimary,
  loadoutSkillsLabel,
  lobbySubtitle,
  openRegister,
  playerAvatarSrc,
  playerBadge,
  playerMeta,
  playerName,
  previewSets,
  primaryAction,
  quickActions,
  railItems,
  ratingLeaderboard,
  recentReplayCount,
  replayTotalCount,
  secondaryAction,
  skillTags,
  syncDetail,
  tertiaryAction,
  topStatusItems
}: HomePageState) {
  const isPlayerOnline = playerMeta !== "未登录";
  const replayDetail = replayTotalCount > 0 ? `战报库 ${replayTotalCount} 条` : "完赛后生成";
  const operationDeckStats = [
    { label: "MODE", value: "ZOMBIE", detail: battleModeDetail, tone: "data" },
    { label: "SYNC", value: "OK", detail: syncDetail, tone: "ready" },
    { label: "REPLAY", value: String(recentReplayCount), detail: replayDetail, tone: recentReplayCount > 0 ? "data" : "idle" }
  ] as const;
  const homeMenuIntelCards = [
    { eyebrow: "战斗模式", value: "ZOMBIE", detail: battleModeDetail },
    { eyebrow: "同步协议", value: "权威同步", detail: syncDetail },
    { eyebrow: "回放链路", value: String(recentReplayCount), detail: replayDetail },
    { eyebrow: "评级状态", value: currentRatingLabel, detail: "战斗评分 / Profile Score" }
  ];

  return (
    <>
      <LobbyShell
        brand="BATTLE RIFT"
        title="Battle Rift"
        subtitle={lobbySubtitle}
        playerName={playerName}
        playerBadge={playerBadge}
        playerAvatarSrc={playerAvatarSrc}
        playerMeta={playerMeta}
        playerRating={currentRatingLabel}
        currentLoadoutLabel={currentLoadoutLabel}
        skillTags={skillTags}
        quickActions={quickActions}
        previewSets={previewSets}
        primaryAction={primaryAction}
        secondaryAction={secondaryAction}
        tertiaryAction={tertiaryAction}
        railItems={railItems}
        topStatusItems={topStatusItems}
        leftDock={<LeaderboardCard title="贡献榜" eyebrow="贡献 Top 10" valueLabel="贡献" activeStatus="榜单接入" emptyStatus="等待战报" entries={contributionLeaderboard} />}
        rightDock={<LeaderboardCard title="评分榜" eyebrow="评分 Top 10" valueLabel="评级" activeStatus="赛季同步" emptyStatus="等待对局" entries={ratingLeaderboard} />}
        menuBody={
          <div className="home-menu">
            <div className="home-menu__brandplate">
              <span className="home-menu__kicker">BATTLE RIFT / 钢铁战备大厅</span>
              <div className="home-menu__emblem" aria-hidden="true">
                <span className="home-menu__emblem-ring home-menu__emblem-ring--outer" />
                <span className="home-menu__emblem-ring home-menu__emblem-ring--inner" />
                <b>BR</b>
              </div>
              <div className="home-menu__logo" aria-hidden="true">
                BATTLE<span>RIFT</span>
              </div>
              <strong className="home-menu__campaign-title">金属战役中枢</strong>
              <p>快节奏 3v3 竞技场 · 武装同步完成，等待投放</p>
              <div className="home-menu__brand-lines" aria-hidden="true">
                <span />
                <span />
                <span />
              </div>
            </div>
            <div className="home-menu__kit" aria-label="当前战备">
              <span>主武器：{loadoutPrimary}</span>
              <span>战术模块：{loadoutSkillsLabel}</span>
            </div>
            <section className="home-menu__operation-deck" aria-label="战备指挥台">
              <header className="home-menu__deck-header">
                <div>
                  <span>OPERATION DECK</span>
                  <strong>战备指挥台</strong>
                </div>
                <p className={`home-menu__deck-state${isPlayerOnline ? " home-menu__deck-state--online" : ""}`}>
                  <i aria-hidden="true" />
                  {isPlayerOnline ? "账号在线" : "未登录"}
                </p>
              </header>
              <div className="home-menu__deck-core">
                <article className="home-menu__deck-identity">
                  <small>当前指挥员</small>
                  <strong>{playerName}</strong>
                  <span>{isPlayerOnline ? `认证标识 ${playerBadge}` : "登录后同步邮件与好友"}</span>
                </article>
                <article className="home-menu__deck-rating">
                  <small>当前评级</small>
                  <strong>{currentRatingLabel}</strong>
                  <span>战斗评分 / Profile Score</span>
                </article>
              </div>
              <div className="home-menu__deck-metrics">
                {operationDeckStats.map((stat) => (
                  <article key={stat.label} className={`home-menu__deck-metric home-menu__deck-metric--${stat.tone}`}>
                    <i aria-hidden="true" />
                    <small>{stat.label}</small>
                    <strong>{stat.value}</strong>
                    <span>{stat.detail}</span>
                  </article>
                ))}
              </div>
              <div className="home-menu__deck-bars" aria-hidden="true">
                <span />
                <span />
                <span />
              </div>
            </section>
            <div className="home-menu__intel-grid" aria-label="大厅战情摘要">
              {homeMenuIntelCards.map((card) => (
                <article key={card.eyebrow} className="home-menu__intel-card">
                  <small>{card.eyebrow}</small>
                  <strong>{card.value}</strong>
                  <span>{card.detail}</span>
                </article>
              ))}
            </div>
            <div className="home-menu__status-strip" aria-hidden="true">
              <span>核心在线</span>
              <span>装甲锁定</span>
              <span>投放就绪</span>
            </div>
            {!isPlayerOnline ? (
              <button type="button" className="home-menu__register" onClick={openRegister}>
                创建指挥员
              </button>
            ) : null}
          </div>
        }
      />

      {authMode ? <AuthOverlay initialMode={authMode} onClose={closeAuthOverlay} onSuccess={completeAuth} /> : null}
    </>
  );
}

function LeaderboardCard({
  title,
  eyebrow,
  valueLabel,
  activeStatus,
  emptyStatus,
  entries
}: {
  title: string;
  eyebrow: string;
  valueLabel: string;
  activeStatus: string;
  emptyStatus: string;
  entries: HomeLeaderboardEntry[];
}) {
  const rows = Array.from({ length: 10 }, (_, index) => entries[index] ?? null);
  const occupiedSlots = Math.min(entries.length, rows.length);

  return (
    <aside className="lobby-side-card" aria-label={title}>
      <header>
        <div>
          <small>{eyebrow}</small>
          <strong>{title}</strong>
        </div>
        <span className="lobby-side-card__signal">{occupiedSlots}/10</span>
      </header>
      <div className="lobby-side-card__summary" aria-label={`${title}状态`}>
        <span>真实记录</span>
        <strong>{occupiedSlots}</strong>
        <b>{occupiedSlots > 0 ? activeStatus : emptyStatus}</b>
      </div>
      <div className="lobby-side-card__list">
        <div className="lobby-side-card__table-head">
          <span>#</span>
          <span>指挥官</span>
          <span>{valueLabel}</span>
        </div>
        {rows.map((row, index) =>
          row ? (
            <article key={row.handle} className={row.rank <= 3 ? "lobby-side-card__row--podium" : undefined}>
              <span className="lobby-side-card__rank">{row.rank}</span>
              <strong>{row.handle}</strong>
              <b>{row.value}</b>
            </article>
          ) : (
            <article key={index} className="lobby-side-card__empty">
              <span aria-hidden="true" />
              <strong>暂无记录</strong>
              <b>—</b>
            </article>
          )
        )}
      </div>
    </aside>
  );
}
