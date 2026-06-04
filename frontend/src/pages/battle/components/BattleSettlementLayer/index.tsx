import type { LocalBattleReturnSummary } from "../../../../runtime/battle/local/state/battleLocalGateway";
import type { MatchPhase } from "../../objects/BattlePageState";
import { BattleSettlementOverlay } from "./components/BattleSettlementOverlay";

interface BattleSettlementLayerProps {
  matchPhase: MatchPhase;
  summary: LocalBattleReturnSummary | null;
  replayId: string | null;
  onNewMatch: () => void;
}

export function BattleSettlementLayer({
  matchPhase,
  summary,
  replayId,
  onNewMatch
}: BattleSettlementLayerProps) {
  if (matchPhase !== "settled" || !summary) {
    return null;
  }

  return <BattleSettlementOverlay summary={summary} replayId={replayId} onNewMatch={onNewMatch} />;
}
