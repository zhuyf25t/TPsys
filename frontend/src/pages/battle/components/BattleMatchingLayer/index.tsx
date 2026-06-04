import type { ComponentProps } from "react";
import type { MatchPhase } from "../../objects/BattlePageState";
import { BattleEntryBlockedOverlay } from "./components/BattleEntryBlockedOverlay";
import { MatchingOverlay } from "./components/MatchingOverlay";

type MatchingOverlayProps = ComponentProps<typeof MatchingOverlay>;

interface BattleMatchingLayerProps {
  entryBlockNotice: string | null;
  matchPhase: MatchPhase;
  countdownMs: MatchingOverlayProps["countdownMs"];
  loadout: MatchingOverlayProps["loadout"];
  queueState: MatchingOverlayProps["queueState"];
  selectedBattleModeId: MatchingOverlayProps["selectedBattleModeId"];
  battleModeOptions: MatchingOverlayProps["battleModeOptions"];
  onBattleModeChange: MatchingOverlayProps["onBattleModeChange"];
}

export function BattleMatchingLayer({
  entryBlockNotice,
  matchPhase,
  countdownMs,
  loadout,
  queueState,
  selectedBattleModeId,
  battleModeOptions,
  onBattleModeChange
}: BattleMatchingLayerProps) {
  if (entryBlockNotice) {
    return <BattleEntryBlockedOverlay message={entryBlockNotice} />;
  }

  if (matchPhase !== "matching") {
    return null;
  }

  return (
    <MatchingOverlay
      countdownMs={countdownMs}
      loadout={loadout}
      queueState={queueState}
      selectedBattleModeId={selectedBattleModeId}
      battleModeOptions={battleModeOptions}
      onBattleModeChange={onBattleModeChange}
    />
  );
}
