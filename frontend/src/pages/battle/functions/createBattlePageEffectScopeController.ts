import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { LocalBattleReturnSummary } from "../../../runtime/battle/local/state/battleLocalGateway";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { AuthoritativeCommandHistoryStore } from "../objects/AuthoritativeCommandHistory";
import type { BattleDrawerId, MatchPhase } from "../objects/BattlePageState";

interface MutableRef<T> {
  current: T;
}

interface BattlePageEffectScopeControllerOptions {
  readonly runtimeRootRef: MutableRef<HTMLDivElement | null>;
  readonly hudRootRef: MutableRef<HTMLDivElement | null>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly discardSessionOnNextTeardownRef: MutableRef<boolean>;
  readonly queueStateRef: MutableRef<MatchmakingQueueState | null>;
  readonly localAuthoritativePlayerIdRef: MutableRef<string | null>;
  readonly replayFramesRef: MutableRef<ReplayFrame[]>;
  readonly lastReplaySampleFrameRef: MutableRef<ReplayFrame | null>;
  readonly lastReplaySampleElapsedRef: MutableRef<number | null>;
  readonly lastActiveSessionPersistedAtRef: MutableRef<number>;
  readonly countdownStartedAtRef: MutableRef<number | null>;
  readonly matchWaitDeadlineRef: MutableRef<number | null>;
  readonly battleDurationDeadlineRef: MutableRef<number | null>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly authoritativeBattleStateRef: MutableRef<AuthoritativeBattleState | null>;
  readonly authoritativeStateRequestInFlightRef: MutableRef<boolean>;
  readonly authoritativeCommandRequestInFlightRef: MutableRef<boolean>;
  readonly authoritativeCommandUplinkPendingRef: MutableRef<boolean>;
  readonly authoritativeCommandSeqRef: MutableRef<number>;
  readonly authoritativeCommandHistoryRef: MutableRef<AuthoritativeCommandHistoryStore>;
  readonly authoritativeFinalizationInFlightRef: MutableRef<boolean>;
  readonly sharedAuthoritativeRuntimeRef: MutableRef<boolean>;
  readonly authoritativeFirstFrameAppliedRef: MutableRef<boolean>;
  readonly authoritativePreparedSkillRef: MutableRef<PreparedSkill>;
  readonly backendQueueJoinPendingRef: MutableRef<boolean>;
  readonly clearTransientNotice: () => void;
  readonly setCurrentResultSummary: (summary: LocalBattleReturnSummary | null) => void;
  readonly setCurrentReplayId: (replayId: string | null) => void;
  readonly setActiveDrawer: (drawer: BattleDrawerId | null) => void;
  readonly setEntryBlockNotice: (message: string | null) => void;
  readonly setMatchPhase: (phase: MatchPhase) => void;
  readonly setQueueState: (state: MatchmakingQueueState | null) => void;
}

interface BattleEntryBlockInput {
  readonly blocked: boolean;
  readonly message: string;
}

export function createBattlePageEffectScopeController({
  runtimeRootRef,
  hudRootRef,
  finalizedRef,
  battleStartLockedRef,
  discardSessionOnNextTeardownRef,
  queueStateRef,
  localAuthoritativePlayerIdRef,
  replayFramesRef,
  lastReplaySampleFrameRef,
  lastReplaySampleElapsedRef,
  lastActiveSessionPersistedAtRef,
  countdownStartedAtRef,
  matchWaitDeadlineRef,
  battleDurationDeadlineRef,
  battleIdRef,
  activeSessionEpochRef,
  authoritativeBattleStateRef,
  authoritativeStateRequestInFlightRef,
  authoritativeCommandRequestInFlightRef,
  authoritativeCommandUplinkPendingRef,
  authoritativeCommandSeqRef,
  authoritativeCommandHistoryRef,
  authoritativeFinalizationInFlightRef,
  sharedAuthoritativeRuntimeRef,
  authoritativeFirstFrameAppliedRef,
  authoritativePreparedSkillRef,
  backendQueueJoinPendingRef,
  clearTransientNotice,
  setCurrentResultSummary,
  setCurrentReplayId,
  setActiveDrawer,
  setEntryBlockNotice,
  setMatchPhase,
  setQueueState
}: BattlePageEffectScopeControllerOptions) {
  const resetRuntimeScope = (): void => {
    finalizedRef.current = false;
    battleStartLockedRef.current = false;
    discardSessionOnNextTeardownRef.current = false;
    queueStateRef.current = null;
    localAuthoritativePlayerIdRef.current = null;
    setCurrentResultSummary(null);
    setCurrentReplayId(null);
    setActiveDrawer(null);
    replayFramesRef.current = [];
    lastReplaySampleFrameRef.current = null;
    lastReplaySampleElapsedRef.current = null;
    lastActiveSessionPersistedAtRef.current = 0;
    countdownStartedAtRef.current = null;
    matchWaitDeadlineRef.current = null;
    battleDurationDeadlineRef.current = null;
    battleIdRef.current = null;
    activeSessionEpochRef.current = null;
    authoritativeBattleStateRef.current = null;
    authoritativeStateRequestInFlightRef.current = false;
    authoritativeCommandRequestInFlightRef.current = false;
    authoritativeCommandUplinkPendingRef.current = false;
    authoritativeCommandSeqRef.current = 0;
    authoritativeCommandHistoryRef.current.clear();
    authoritativeFinalizationInFlightRef.current = false;
    sharedAuthoritativeRuntimeRef.current = false;
    authoritativeFirstFrameAppliedRef.current = false;
    authoritativePreparedSkillRef.current = null;
    backendQueueJoinPendingRef.current = false;
    clearTransientNotice();
    runtimeRootRef.current?.replaceChildren();
    hudRootRef.current?.replaceChildren();
  };

  const applyEntryBlock = ({ blocked, message }: BattleEntryBlockInput): (() => void) | null => {
    if (!blocked) {
      setEntryBlockNotice(null);
      return null;
    }

    setEntryBlockNotice(message);
    setMatchPhase("matching");
    setQueueState(null);
    return () => {
      clearTransientNotice();
      runtimeRootRef.current?.replaceChildren();
      hudRootRef.current?.replaceChildren();
    };
  };

  return {
    applyEntryBlock,
    resetRuntimeScope
  };
}
