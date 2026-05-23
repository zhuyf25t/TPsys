import { Link } from "react-router-dom";
import { QuickPreviewOverlay } from "../../../../shared/ui/QuickPreviewOverlay";
import { BattleChrome } from "../../components/BattleChrome";
import { buildBattleDrawer } from "./lib/battleDrawerPresenter";
import { QUICK_LEFT, QUICK_RIGHT } from "./lib/battlePageTypes";
import { BattleSettlementOverlay } from "./lib/BattleSettlementOverlay";
import { MatchingOverlay } from "./lib/MatchingOverlay";
import { useBattlePageRuntime } from "./lib/useBattlePageRuntime";

/** 中文名：战斗页面（BattlePage）。游戏职责：连接匹配、Phaser runtime、HUD、抽屉和结算层。 */
export function BattlePage() {
  const runtime = useBattlePageRuntime();

  const settlementOverlay =
    runtime.matchPhase === "settled" && runtime.currentResultSummary ? (
      <BattleSettlementOverlay summary={runtime.currentResultSummary} replayId={runtime.currentReplayId} onNewMatch={runtime.startNewMatch} />
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
  const leftButtons = shouldShowDrawerButtons
    ? QUICK_LEFT.map((item) => ({
        label: item.label,
        iconKey: item.iconKey,
        onClick: () => runtime.openDrawer(item.id)
      }))
    : [];
  const rightButtons = shouldShowDrawerButtons
    ? QUICK_RIGHT.map((item) => ({
        label: item.label,
        iconKey: item.iconKey,
        onClick: () => runtime.openDrawer(item.id),
        badgeCount:
          item.id === "mails"
            ? runtime.unreadMailCount
            : item.id === "social"
              ? runtime.friendRequestPreview.badgeCount
              : undefined
      }))
    : [];

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
            selectedBattleModeId={runtime.selectedBattleModeId}
            battleModeOptions={runtime.battleModeOptions}
            onBattleModeChange={runtime.selectBattleMode}
          />
        ) : null
      }
      settlementOverlay={settlementOverlay}
      drawerOverlay={drawerOverlay}
    >
      {runtime.entryBlockNotice ? null : runtime.transientNotice ? (
        <div
          key={runtime.transientNotice.id}
          className="absolute left-1/2 top-6 z-30 -translate-x-1/2 rounded border border-cyan-200/40 bg-slate-950/90 px-4 py-2 text-sm font-bold text-cyan-100 shadow-lg"
          role="status"
          aria-live="polite"
        >
          {runtime.transientNotice.message}
        </div>
      ) : null}
      <div ref={runtime.runtimeRootRef} className="h-full w-full" aria-label="battle runtime" />
      <div id="hud-root" ref={runtime.hudRootRef} className="pointer-events-none absolute inset-0 z-10" />
    </BattleChrome>
  );
}

function BattleEntryBlockedOverlay({ message }: { message: string }) {
  return (
    <div className="absolute inset-0 z-30 grid place-items-center bg-slate-950/80 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="需要登录">
      <div className="w-full max-w-4xl rounded border border-white/10 bg-slate-950/95 p-5 text-slate-100 shadow-2xl shadow-black/60">
        <header className="flex flex-col gap-4 border-b border-white/10 pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <small className="text-xs font-black uppercase tracking-[0.22em] text-red-200">正式匹配已锁定</small>
            <strong className="mt-2 block text-2xl font-black text-white">请先登录账号</strong>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <BlockedMetric label="状态" value="Visitor" />
            <BlockedMetric label="结果写入" value="禁止" />
            <BlockedMetric label="Rating" value="不计入" />
          </div>
        </header>

        <section className="grid gap-5 py-5 lg:grid-cols-[1fr_320px]">
          <div>
            <h2 className="text-2xl font-black text-white">{message}</h2>
            <p className="mt-3 text-sm leading-6 text-slate-300">
              游客可以浏览大厅、配装、榜单和回放，但不能进入正式匹配，也不能写入战绩、回放或评分。
            </p>
          </div>
          <div className="rounded border border-white/10 bg-white/[0.04] p-4">
            <small className="text-xs font-black uppercase tracking-[0.2em] text-amber-200">安全规则</small>
            <strong className="mt-2 block text-white">正式对战需要后端身份 session</strong>
            <span className="mt-2 block text-sm leading-6 text-slate-400">前端不会加入队列，后端也会拒绝 anonymous/Visitor ticket。</span>
          </div>
        </section>

        <footer className="flex flex-wrap justify-end gap-3 border-t border-white/10 pt-4">
          <Link className="rounded border border-amber-200/50 bg-amber-300/20 px-4 py-2 text-sm font-black text-amber-50 transition hover:bg-amber-300/30" to="/">
            返回大厅登录
          </Link>
          <Link className="rounded border border-white/10 bg-white/5 px-4 py-2 text-sm font-bold text-slate-200 transition hover:bg-white/10" to="/rating">
            查看榜单
          </Link>
        </footer>
      </div>
    </div>
  );
}

function BlockedMetric({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded border border-white/10 bg-white/[0.04] px-4 py-3">
      <span className="text-xs text-slate-400">{label}</span>
      <strong className="mt-1 block text-white">{value}</strong>
    </article>
  );
}
