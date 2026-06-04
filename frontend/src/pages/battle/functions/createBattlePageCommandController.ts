import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import {
  resolveBattlePlayMode,
  type BattlePlayModeId
} from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { ActiveBattleSessionOwner, MatchPhase } from "../objects/BattlePageState";
import { clearActiveBattleSession, publishActiveBattleSessionEpoch } from "../stores/activeBattleSessionStore";

interface MutableRef<T> {
  current: T;
}

interface BattlePageCommandControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly matchPhase: MatchPhase;
  readonly selectedBattleModeId: BattlePlayModeId;
  readonly entryBlocked: boolean;
  readonly entryBlockedMessage: string;
  readonly runtimeHandleRef: MutableRef<BattleRuntimeHandle | null>;
  readonly discardSessionOnNextTeardownRef: MutableRef<boolean>;
  readonly newBattleResetPendingRef: MutableRef<boolean>;
  readonly countdownStartedAtRef: MutableRef<number | null>;
  readonly matchWaitDeadlineRef: MutableRef<number | null>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly authoritativePreparedSkillRef: MutableRef<PreparedSkill>;
  readonly setQueueState: (state: MatchmakingQueueState | null) => void;
  readonly setSelectedBattleModeId: (modeId: BattlePlayModeId) => void;
  readonly setMatchNonce: (updater: (value: number) => number) => void;
  readonly setEntryBlockNotice: (message: string | null) => void;
}

export function createBattlePageCommandController({
  owner,
  matchPhase,
  selectedBattleModeId,
  entryBlocked,
  entryBlockedMessage,
  runtimeHandleRef,
  discardSessionOnNextTeardownRef,
  newBattleResetPendingRef,
  countdownStartedAtRef,
  matchWaitDeadlineRef,
  battleIdRef,
  activeSessionEpochRef,
  authoritativePreparedSkillRef,
  setQueueState,
  setSelectedBattleModeId,
  setMatchNonce,
  setEntryBlockNotice
}: BattlePageCommandControllerOptions) {
  const resetBattleProgress = (): void => {
    discardSessionOnNextTeardownRef.current = true;
    newBattleResetPendingRef.current = true;
    countdownStartedAtRef.current = null;
    matchWaitDeadlineRef.current = null;
    battleIdRef.current = null;
    activeSessionEpochRef.current = publishActiveBattleSessionEpoch(owner);
    clearActiveBattleSession(owner);
  };

  const selectBattleMode = (modeId: BattlePlayModeId): void => {
    const nextModeId = resolveBattlePlayMode(modeId).modeId;
    if (nextModeId === selectedBattleModeId || matchPhase !== "matching") {
      return;
    }

    resetBattleProgress();
    setQueueState(null);
    setSelectedBattleModeId(nextModeId);
    setMatchNonce((value) => value + 1);
  };

  const startNewMatch = (): void => {
    if (entryBlocked) {
      setEntryBlockNotice(entryBlockedMessage);
      return;
    }

    resetBattleProgress();
    authoritativePreparedSkillRef.current = null;
    runtimeHandleRef.current?.setAuthoritativePreparedSkill(null);
    setMatchNonce((value) => value + 1);
  };

  return {
    selectBattleMode,
    startNewMatch
  };
}
