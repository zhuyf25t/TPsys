import { QuickPreviewOverlay } from "../../shared/ui/QuickPreviewOverlay";
import { BattleChrome } from "../../components/battle/BattleChrome";
import { buildBattleDrawer } from "./non-game/battleDrawerPresenter";
import { QUICK_LEFT, QUICK_RIGHT } from "../../hooks/battle-page/battlePageTypes";
import { BattleGameScreen } from "./game-screen/BattleGameScreen";
import { BattleEntryBlockedOverlay } from "./non-game/BattleEntryBlockedOverlay";
import { BattleSettlementOverlay } from "./non-game/BattleSettlementOverlay";
import { MatchingOverlay } from "./non-game/MatchingOverlay";
import { useBattlePageRuntime } from "../../hooks/battle-page/useBattlePageRuntime";

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
        runtime.markDrawerMailRead
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
      <BattleGameScreen
        entryBlockNotice={runtime.entryBlockNotice}
        transientNotice={runtime.transientNotice}
        runtimeRootRef={runtime.runtimeRootRef}
        hudRootRef={runtime.hudRootRef}
      />
    </BattleChrome>
  );
}
