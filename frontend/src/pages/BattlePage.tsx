import { buildBattleDrawer } from "../features/battle/page/battleDrawerPresenter";
import { BattleSettlementOverlay } from "../features/battle/page/BattleSettlementOverlay";
import { MatchingOverlay } from "../features/battle/page/MatchingOverlay";
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
        runtime.currentUser?.handle
      )}
      onClose={runtime.closeDrawer}
    />
  ) : null;

  return (
    <BattleChrome
      phase={runtime.matchPhase}
      leftButtons={[]}
      rightButtons={[]}
      matchingOverlay={
        runtime.matchPhase === "matching" ? (
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
      {runtime.transientNotice ? (
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
