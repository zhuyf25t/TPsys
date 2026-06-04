import { useState, type Dispatch, type SetStateAction } from "react";
import type { LocalBattleReturnSummary } from "../../../runtime/battle/local/state/battleLocalGateway";
import {
  DEFAULT_BATTLE_MODE_ID,
  type BattlePlayModeId
} from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { BattleDrawerId, MatchPhase } from "../objects/BattlePageState";
import { MATCHMAKING_DURATION_MS } from "../objects/BattlePageTiming";

interface BattlePageViewState {
  readonly matchNonce: number;
  readonly setMatchNonce: Dispatch<SetStateAction<number>>;
  readonly matchCountdownMs: number;
  readonly setMatchCountdownMs: Dispatch<SetStateAction<number>>;
  readonly currentResultSummary: LocalBattleReturnSummary | null;
  readonly setCurrentResultSummary: Dispatch<SetStateAction<LocalBattleReturnSummary | null>>;
  readonly currentReplayId: string | null;
  readonly setCurrentReplayId: Dispatch<SetStateAction<string | null>>;
  readonly matchPhase: MatchPhase;
  readonly setMatchPhase: Dispatch<SetStateAction<MatchPhase>>;
  readonly queueState: MatchmakingQueueState | null;
  readonly setQueueState: Dispatch<SetStateAction<MatchmakingQueueState | null>>;
  readonly selectedBattleModeId: BattlePlayModeId;
  readonly setSelectedBattleModeId: Dispatch<SetStateAction<BattlePlayModeId>>;
  readonly activeDrawer: BattleDrawerId | null;
  readonly setActiveDrawer: Dispatch<SetStateAction<BattleDrawerId | null>>;
  readonly entryBlockNotice: string | null;
  readonly setEntryBlockNotice: Dispatch<SetStateAction<string | null>>;
}

export function useBattlePageViewState(): BattlePageViewState {
  const [matchNonce, setMatchNonce] = useState(0);
  const [matchCountdownMs, setMatchCountdownMs] = useState(MATCHMAKING_DURATION_MS);
  const [currentResultSummary, setCurrentResultSummary] = useState<LocalBattleReturnSummary | null>(null);
  const [currentReplayId, setCurrentReplayId] = useState<string | null>(null);
  const [matchPhase, setMatchPhase] = useState<MatchPhase>("matching");
  const [queueState, setQueueState] = useState<MatchmakingQueueState | null>(null);
  const [selectedBattleModeId, setSelectedBattleModeId] = useState<BattlePlayModeId>(DEFAULT_BATTLE_MODE_ID);
  const [activeDrawer, setActiveDrawer] = useState<BattleDrawerId | null>(null);
  const [entryBlockNotice, setEntryBlockNotice] = useState<string | null>(null);

  return {
    matchNonce,
    setMatchNonce,
    matchCountdownMs,
    setMatchCountdownMs,
    currentResultSummary,
    setCurrentResultSummary,
    currentReplayId,
    setCurrentReplayId,
    matchPhase,
    setMatchPhase,
    queueState,
    setQueueState,
    selectedBattleModeId,
    setSelectedBattleModeId,
    activeDrawer,
    setActiveDrawer,
    entryBlockNotice,
    setEntryBlockNotice
  };
}
