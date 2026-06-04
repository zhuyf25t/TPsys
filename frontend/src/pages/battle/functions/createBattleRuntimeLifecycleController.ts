import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import {
  finalizeLocalBattle,
  type LocalBattleReturnSummary
} from "../../../runtime/battle/local/state/battleLocalGateway";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import type { ActiveBattleSessionOwner, MatchPhase } from "../objects/BattlePageState";
import { clearActiveBattleSession } from "../stores/activeBattleSessionStore";
import {
  AUTHORITATIVE_RESULT_READY_RETRY_MS,
  AUTHORITATIVE_RESULT_READY_TIMEOUT_MS
} from "../objects/BattlePageRuntimeConfig";
import { resolveLocalRuntimeFinalizationPlan } from "./battleRuntimeFinalizationPlans";
import {
  resolveBattleRuntimeFinalizationDecision,
  resolveSharedAuthoritativeFinalizationResultPlan,
  resolveSharedAuthoritativeFinalizationStartDecision
} from "./battleRuntimeLifecyclePlans";
import { loadAuthoritativeResultSummaryWhenReady } from "./loadAuthoritativeResultSummaryWhenReady";
import type { AuthoritativeSessionRestoreIdentity } from "./authoritativeSessionRestore";
import { createBattleCompletedSessionRecoveryController } from "./createBattleCompletedSessionRecoveryController";
import { createBattleRuntimePersistenceController } from "./createBattleRuntimePersistenceController";

interface MutableRef<T> {
  current: T;
}

export type StartBattleRuntime = (
  runtimeInitialSnapshot?: GameSnapshot | null,
  restoredReplayFrames?: ReplayFrame[],
  restoredLastReplaySampleElapsed?: number | null,
  initialParticipants?: BattleInitialParticipantsConfig,
  battleId?: string,
  initialAuthoritativeState?: AuthoritativeBattleState | null,
  sharedAuthoritativeRuntime?: boolean
) => void;

interface BattleRuntimeLifecycleControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly runtimeHandleRef: MutableRef<BattleRuntimeHandle | null>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly countdownStartedAtRef: MutableRef<number | null>;
  readonly battleDurationDeadlineRef: MutableRef<number | null>;
  readonly matchWaitDeadlineRef: MutableRef<number | null>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly sharedAuthoritativeRuntimeRef: MutableRef<boolean>;
  readonly authoritativeFirstFrameAppliedRef: MutableRef<boolean>;
  readonly authoritativeFinalizationInFlightRef: MutableRef<boolean>;
  readonly authoritativeBattleStateRef: MutableRef<AuthoritativeBattleState | null>;
  readonly replayFramesRef: MutableRef<ReplayFrame[]>;
  readonly lastReplaySampleFrameRef: MutableRef<ReplayFrame | null>;
  readonly lastReplaySampleElapsedRef: MutableRef<number | null>;
  readonly lastActiveSessionPersistedAtRef: MutableRef<number>;
  readonly clearSnapshotTimer: () => void;
  readonly clearBattleEndTimer: () => void;
  readonly clearBattleDurationTimer: () => void;
  readonly clearMatchStartTimer: () => void;
  readonly clearQueuePollingTimer: () => void;
  readonly clearRoomPresenceTimer: () => void;
  readonly stopAuthoritativeBattleBridge: () => void;
  readonly readRuntimeSnapshot: () => GameSnapshot | null;
  readonly resolveRuntimeBattleId: () => string;
  readonly resolveRuntimeMapId: () => string;
  readonly resolveLocalAuthoritativePlayerId: () => string;
  readonly resolveLocalAuthoritativeTicketId: () => string;
  readonly shouldStoreRuntimeCompletedSession: (snapshot: GameSnapshot) => boolean;
  readonly shouldFinalizeRuntimeSnapshot: (
    snapshot: GameSnapshot | null,
    durationExpired: boolean,
    forceCurrentSnapshot: boolean
  ) => snapshot is GameSnapshot;
  readonly shouldFinalizeRuntimeSnapshotOnExit: (snapshot: GameSnapshot | null) => snapshot is GameSnapshot;
  readonly isBattleDurationExpired: () => boolean;
  readonly isQueueJoinCancelled: () => boolean;
  readonly applyRecoveredAuthoritativeIdentity: (identity: AuthoritativeSessionRestoreIdentity) => void;
  readonly startBattleRuntime: StartBattleRuntime;
  readonly setMatchPhase: (phase: MatchPhase) => void;
  readonly setCurrentResultSummary: (summary: LocalBattleReturnSummary | null) => void;
  readonly setCurrentReplayId: (replayId: string | null) => void;
  readonly setMatchNonce: (updater: (value: number) => number) => void;
}

export function createBattleRuntimeLifecycleController({
  owner,
  runtimeHandleRef,
  finalizedRef,
  battleStartLockedRef,
  countdownStartedAtRef,
  battleDurationDeadlineRef,
  matchWaitDeadlineRef,
  battleIdRef,
  activeSessionEpochRef,
  sharedAuthoritativeRuntimeRef,
  authoritativeFirstFrameAppliedRef,
  authoritativeFinalizationInFlightRef,
  authoritativeBattleStateRef,
  replayFramesRef,
  lastReplaySampleFrameRef,
  lastReplaySampleElapsedRef,
  lastActiveSessionPersistedAtRef,
  clearSnapshotTimer,
  clearBattleEndTimer,
  clearBattleDurationTimer,
  clearMatchStartTimer,
  clearQueuePollingTimer,
  clearRoomPresenceTimer,
  stopAuthoritativeBattleBridge,
  readRuntimeSnapshot,
  resolveRuntimeBattleId,
  resolveRuntimeMapId,
  resolveLocalAuthoritativePlayerId,
  resolveLocalAuthoritativeTicketId,
  shouldStoreRuntimeCompletedSession,
  shouldFinalizeRuntimeSnapshot,
  shouldFinalizeRuntimeSnapshotOnExit,
  isBattleDurationExpired,
  isQueueJoinCancelled,
  applyRecoveredAuthoritativeIdentity,
  startBattleRuntime,
  setMatchPhase,
  setCurrentResultSummary,
  setCurrentReplayId,
  setMatchNonce
}: BattleRuntimeLifecycleControllerOptions) {
  const {
    buildActiveSession,
    persistRuntime,
    pushReplayFrame,
    writeActiveSession,
    writeCompletedSession
  } = createBattleRuntimePersistenceController({
    owner,
    isRuntimeActive: () => runtimeHandleRef.current !== null,
    finalizedRef,
    battleIdRef,
    activeSessionEpochRef,
    sharedAuthoritativeRuntimeRef,
    authoritativeFirstFrameAppliedRef,
    replayFramesRef,
    lastReplaySampleFrameRef,
    lastReplaySampleElapsedRef,
    lastActiveSessionPersistedAtRef,
    readRuntimeSnapshot,
    resolveRuntimeBattleId,
    resolveRuntimeMapId,
    resolveLocalAuthoritativePlayerId,
    resolveLocalAuthoritativeTicketId,
    shouldStoreRuntimeCompletedSession
  });

  const tearDownRuntime = (): void => {
    clearSnapshotTimer();
    clearBattleEndTimer();
    clearBattleDurationTimer();
    clearMatchStartTimer();
    clearQueuePollingTimer();
    clearRoomPresenceTimer();
    countdownStartedAtRef.current = null;
    battleDurationDeadlineRef.current = null;
    matchWaitDeadlineRef.current = null;
    battleIdRef.current = null;
    activeSessionEpochRef.current = null;
    stopAuthoritativeBattleBridge();
    sharedAuthoritativeRuntimeRef.current = false;
    authoritativeFirstFrameAppliedRef.current = false;
    runtimeHandleRef.current?.destroy();
    runtimeHandleRef.current = null;
    battleStartLockedRef.current = false;
  };

  const finalizeLocalBattleWithFreshIdFallback = (
    input: Parameters<typeof finalizeLocalBattle>[0]
  ): ReturnType<typeof finalizeLocalBattle> => {
    const finalized = finalizeLocalBattle(input);
    if (finalized || !input.battleId) {
      return finalized;
    }

    return finalizeLocalBattle({
      ...input,
      battleId: resolveRuntimeBattleId()
    });
  };

  const recoverFromUnfinalizableSession = (): void => {
    clearActiveBattleSession(owner);
    finalizedRef.current = true;
    tearDownRuntime();
    setMatchPhase("matching");
    setCurrentResultSummary(null);
    setCurrentReplayId(null);
    setMatchNonce((value) => value + 1);
  };

  const shouldFinalizeRuntimeOnExit = (): boolean => {
    const snapshot = readRuntimeSnapshot();
    return shouldFinalizeRuntimeSnapshotOnExit(snapshot);
  };

  const loadAuthoritativeResultSummaryForBattle = async (
    battleId: string,
    timeoutMs = AUTHORITATIVE_RESULT_READY_TIMEOUT_MS
  ): Promise<LocalBattleReturnSummary | null> =>
    loadAuthoritativeResultSummaryWhenReady({
      battleId,
      ownerHandle: owner.handle,
      timeoutMs,
      retryMs: AUTHORITATIVE_RESULT_READY_RETRY_MS,
      isCancelled: isQueueJoinCancelled,
      readCachedState: (normalizedBattleId) =>
        authoritativeBattleStateRef.current?.battleId === normalizedBattleId ? authoritativeBattleStateRef.current : null,
      writeCachedState: (state) => {
        authoritativeBattleStateRef.current = state;
      }
    });

  const settleAuthoritativeResult = (summary: LocalBattleReturnSummary, battleId: string): void => {
    finalizedRef.current = true;
    clearActiveBattleSession(owner);
    tearDownRuntime();
    setMatchPhase("settled");
    setCurrentResultSummary(summary);
    setCurrentReplayId(battleId);
  };

  const { recoverCompletedSession } = createBattleCompletedSessionRecoveryController({
    owner,
    runtimeHandleRef,
    finalizedRef,
    battleIdRef,
    authoritativeBattleStateRef,
    isQueueJoinCancelled,
    applyRecoveredAuthoritativeIdentity,
    startBattleRuntime,
    loadAuthoritativeResultSummaryForBattle,
    settleAuthoritativeResult,
    finalizeLocalBattleWithFreshIdFallback,
    setMatchPhase,
    setCurrentResultSummary,
    setCurrentReplayId
  });

  const finalizeSharedAuthoritativeRuntime = async (snapshot: GameSnapshot): Promise<void> => {
    const startDecision = resolveSharedAuthoritativeFinalizationStartDecision({
      battleId: battleIdRef.current,
      requestInFlight: authoritativeFinalizationInFlightRef.current,
      finalized: finalizedRef.current
    });
    if (startDecision.kind === "skip") {
      return;
    }

    authoritativeFinalizationInFlightRef.current = true;
    writeActiveSession(snapshot);
    const summary = await loadAuthoritativeResultSummaryForBattle(startDecision.battleId);
    authoritativeFinalizationInFlightRef.current = false;
    const resultPlan = resolveSharedAuthoritativeFinalizationResultPlan({
      battleId: startDecision.battleId,
      summary,
      cancelled: isQueueJoinCancelled(),
      finalized: finalizedRef.current
    });
    if (resultPlan.kind === "skip") {
      return;
    }

    if (resultPlan.kind === "preserve_active_session") {
      writeActiveSession(snapshot);
      return;
    }

    settleAuthoritativeResult(resultPlan.summary, resultPlan.battleId);
  };

  const finalizeRuntime = (
    forceTimeLimit = false,
    forceCurrentSnapshot = false,
    preserveCompletedSession = false
  ): void => {
    const runtime = runtimeHandleRef.current;
    const snapshot = readRuntimeSnapshot();
    const durationExpired = forceTimeLimit && isBattleDurationExpired();
    const finalizationDecision = resolveBattleRuntimeFinalizationDecision({
      runtimeActive: runtime !== null,
      finalized: finalizedRef.current,
      authoritativeFirstFramePending: sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current,
      snapshot,
      shouldFinalizeSnapshot: shouldFinalizeRuntimeSnapshot(snapshot, durationExpired, forceCurrentSnapshot),
      sharedAuthoritativeRuntime: sharedAuthoritativeRuntimeRef.current,
      durationExpired,
      forceCurrentSnapshot
    });
    if (finalizationDecision.kind === "skip") {
      return;
    }

    if (finalizationDecision.kind === "finalize_shared") {
      void finalizeSharedAuthoritativeRuntime(finalizationDecision.snapshot);
      return;
    }

    const finalizationPlan = resolveLocalRuntimeFinalizationPlan({
      snapshot: finalizationDecision.snapshot,
      forceCurrentSnapshot: finalizationDecision.forceCurrentSnapshot,
      durationExpired: finalizationDecision.durationExpired,
      replayFrames: replayFramesRef.current
    });
    replayFramesRef.current = finalizationPlan.replayFrames;

    finalizedRef.current = true;
    pushReplayFrame(finalizationPlan.finalSnapshot, true);
    writeCompletedSession(finalizationPlan.finalSnapshot);

    const finalized = finalizeLocalBattleWithFreshIdFallback({
      battleId: battleIdRef.current ?? undefined,
      snapshot: finalizationPlan.finalSnapshot,
      finishedAt: Date.now(),
      thumbnailDataUrl: runtime?.captureThumbnail() ?? null,
      replayFrames: replayFramesRef.current,
      botOnlyClosure: finalizationPlan.botOnlyClosure,
      allowBotOnlyClosure: true,
      syncBackend: true
    });

    if (!finalized) {
      if (preserveCompletedSession) {
        finalizedRef.current = false;
        return;
      }

      recoverFromUnfinalizableSession();
      return;
    }

    clearActiveBattleSession(owner);
    tearDownRuntime();
    setMatchPhase("settled");
    setCurrentResultSummary(finalized.returnSummary);
    setCurrentReplayId(finalized.replay.id);
  };

  return {
    buildActiveSession,
    finalizeRuntime,
    persistRuntime,
    pushReplayFrame,
    recoverCompletedSession,
    shouldFinalizeRuntimeOnExit,
    tearDownRuntime
  };
}
