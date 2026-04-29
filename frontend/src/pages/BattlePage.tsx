import { Link } from "react-router-dom";
import { buildBattleDrawer } from "../features/battle/page/battleDrawerPresenter";
import { BattleSettlementOverlay } from "../features/battle/page/BattleSettlementOverlay";
import { MatchingOverlay } from "../features/battle/page/MatchingOverlay";
import { QUICK_LEFT, QUICK_RIGHT } from "../features/battle/page/battlePageTypes";
import { useBattlePageRuntime } from "../features/battle/page/useBattlePageRuntime";
import { BattleChrome } from "../shared/ui/BattleChrome";
import { QuickPreviewOverlay } from "../shared/ui/QuickPreviewOverlay";

export function BattlePage() {
  const runtime = useBattlePageRuntime();

  const settlementOverlay =
    runtime.matchPhase === "settled" && runtime.currentResultSummary ? (
      <BattleSettlementOverlay
        summary={runtime.currentResultSummary}
        replayId={runtime.currentReplayId}
        onNewMatch={runtime.startNewMatch}
      />
    ) : null;

  const drawerOverlay = runtime.activeDrawer ? (
    <QuickPreviewOverlay
      {...buildBattleDrawer(
        runtime.activeDrawer,
        runtime.replaySummaries,
        runtime.discussionSummaries,
        runtime.mailSummaries,
        runtime.ratingEntries,
        runtime.friendRequestPreview,
        runtime.currentUser?.handle
      )}
      onClose={runtime.closeDrawer}
    />
  ) : null;
  const shouldShowDrawerButtons = runtime.matchPhase !== "playing" && !runtime.entryBlockNotice;
  const leftButtons = shouldShowDrawerButtons ? QUICK_LEFT.map((item) => ({
    label: item.label,
    iconKey: item.iconKey,
    onClick: () => runtime.openDrawer(item.id)
  })) : [];
  const rightButtons = shouldShowDrawerButtons ? QUICK_RIGHT.map((item) => ({
    label: item.label,
    iconKey: item.iconKey,
    onClick: () => runtime.openDrawer(item.id),
    badgeCount:
      item.id === "mails"
        ? runtime.unreadMailCount
        : item.id === "social"
          ? runtime.friendRequestPreview.badgeCount
          : undefined
  })) : [];

  return (
    <BattleChrome
      phase={runtime.matchPhase}
      leftButtons={leftButtons}
      rightButtons={rightButtons}
      matchingOverlay={
        runtime.entryBlockNotice ? (
          <BattleEntryBlockedOverlay message={runtime.entryBlockNotice} />
        ) : runtime.matchPhase === "matching" ? (
          <MatchingOverlay
            countdownMs={runtime.matchCountdownMs}
            loadout={runtime.loadout}
            queueState={runtime.queueState}
          />
        ) : null
      }
      settlementOverlay={settlementOverlay}
      drawerOverlay={drawerOverlay}
    >
      {runtime.entryBlockNotice ? null : runtime.transientNotice ? (
        <div
          key={runtime.transientNotice.id}
          className="arena-shell__transient-notice"
          role="status"
          aria-live="polite"
        >
          {runtime.transientNotice.message}
        </div>
      ) : null}
      <div ref={runtime.runtimeRootRef} className="arena-shell__runtime" aria-label="battle runtime" />
      <div id="hud-root" ref={runtime.hudRootRef} className="arena-shell__hud-root" />
    </BattleChrome>
  );
}

function BattleEntryBlockedOverlay({ message }: { message: string }) {
  return (
    <div className="arena-shell__overlay" role="dialog" aria-modal="true" aria-label="需要登录">
      <div className="match-board">
        <header className="match-board__header match-board__header--matching">
          <div className="match-board__headline">
            <small>正式匹配已锁定</small>
            <strong>请先登录账号</strong>
          </div>
          <div className="match-board__header-metrics">
            <article>
              <span>状态</span>
              <strong>Visitor</strong>
            </article>
            <article>
              <span>结果写入</span>
              <strong>禁止</strong>
            </article>
            <article>
              <span>Rating</span>
              <strong>不计入</strong>
            </article>
          </div>
        </header>

        <section className="match-board__summary">
          <div className="match-board__summary-copy">
            <h2>{message}</h2>
            <p>游客可以浏览大厅、配装、榜单和回放，但不能进入正式匹配，也不能写入战绩、回放或评分。</p>
          </div>
          <div className="match-board__summary-card">
            <div className="match-board__summary-meta">
              <small>安全规则</small>
              <strong>正式对战需要后端身份 session</strong>
              <span>前端不会加入队列，后端也会拒绝 anonymous/Visitor ticket。</span>
            </div>
          </div>
        </section>

        <footer className="match-board__actions">
          <Link className="match-board__action match-board__action--primary" to="/">
            返回大厅登录
          </Link>
          <Link className="match-board__action match-board__action--ghost" to="/rating">
            查看榜单
          </Link>
        </footer>
      </div>
    </div>
  );
}
