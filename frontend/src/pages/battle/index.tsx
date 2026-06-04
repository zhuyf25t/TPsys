import { BattleChrome } from "./components/BattleChrome";
import { BattleDrawerLayer } from "./components/BattleDrawerLayer";
import { BattleGameScreen } from "./components/BattleGameScreen";
import { BattleMatchingLayer } from "./components/BattleMatchingLayer";
import { BattleSettlementLayer } from "./components/BattleSettlementLayer";
import { buildBattleQuickAccessButtons } from "./functions/buildBattleQuickAccessButtons";
import { useBattlePageRuntime } from "./hooks/useBattlePageRuntime";

/** 中文名：战斗页面（BattlePage）。游戏职责：连接匹配、Phaser runtime、HUD、抽屉和结算层。 */
export function BattlePage() {
  const runtime = useBattlePageRuntime();
  const quickAccessButtons = buildBattleQuickAccessButtons({
    matchPhase: runtime.matchPhase,
    entryBlockNotice: runtime.entryBlockNotice,
    unreadMailCount: runtime.unreadMailCount,
    friendRequestBadgeCount: runtime.friendRequestPreview.badgeCount,
    openDrawer: runtime.openDrawer
  });

  return (
    <BattleChrome
      phase={runtime.matchPhase}
      leftButtons={quickAccessButtons.leftButtons}
      rightButtons={quickAccessButtons.rightButtons}
      matchingOverlay={
        <BattleMatchingLayer
          entryBlockNotice={runtime.entryBlockNotice}
          matchPhase={runtime.matchPhase}
          countdownMs={runtime.matchCountdownMs}
          loadout={runtime.loadout}
          queueState={runtime.queueState}
          selectedBattleModeId={runtime.selectedBattleModeId}
          battleModeOptions={runtime.battleModeOptions}
          onBattleModeChange={runtime.selectBattleMode}
        />
      }
      settlementOverlay={
        <BattleSettlementLayer
          matchPhase={runtime.matchPhase}
          summary={runtime.currentResultSummary}
          replayId={runtime.currentReplayId}
          onNewMatch={runtime.startNewMatch}
        />
      }
      drawerOverlay={
        <BattleDrawerLayer
          activeDrawer={runtime.activeDrawer}
          replaySummaries={runtime.replaySummaries}
          discussionSummaries={runtime.discussionSummaries}
          mailSummaries={runtime.mailSummaries}
          ratingEntries={runtime.ratingEntries}
          friendRequestPreview={runtime.friendRequestPreview}
          onUnreadMailSelect={runtime.markDrawerMailRead}
          onClose={runtime.closeDrawer}
        />
      }
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
