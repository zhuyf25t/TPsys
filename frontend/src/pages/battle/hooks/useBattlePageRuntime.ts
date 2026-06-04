import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { useEffect } from "react";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import {
  getBattleAliveHeroCount,
  isBattleComplete
} from "../../../runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules";
import {
  clearActiveBattleSession
} from "../stores/activeBattleSessionStore";
import {
  type ActiveBattleSession,
  type ActiveBattleSessionOwner
} from "../objects/BattlePageState";
import { type MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { useBattlePageData } from "./useBattlePageData";
import {
  shouldFinalizeBattleSnapshot,
  shouldFinalizeBattleSnapshotOnExit,
  shouldStoreCompletedBattleSession
} from "../functions/battlePageRuntimeHelpers";
import { createBattleRuntimeLifecycleController } from "../functions/createBattleRuntimeLifecycleController";
import { createBattleRuntimeLaunchController } from "../functions/createBattleRuntimeLaunchController";
import { createBattlePageExitPersistenceController } from "../functions/createBattlePageExitPersistenceController";
import { initializeBattlePageSessionProgress } from "../functions/initializeBattlePageSessionProgress";
import {
  createBattleMatchmakingStartupController,
  type BattleMatchmakingQueueRuntimeState
} from "../functions/createBattleMatchmakingStartupController";
import { useBattleAuthoritativeBridgeTimers } from "./useBattleAuthoritativeBridgeTimers";
import { useBattlePageRuntimeRefs } from "./useBattlePageRuntimeRefs";
import { useBattlePageTimers } from "./useBattlePageTimers";
import { useBattlePageUrlIntent } from "./useBattlePageUrlIntent";
import { useBattlePageViewState } from "./useBattlePageViewState";
import { useBattleTransientNotice } from "./useBattleTransientNotice";
import {
  isAuthoritativeBattleFinished as isAuthoritativeBattleFinishedState,
  isAuthoritativeDurationExpired as isAuthoritativeDurationExpiredState,
  resolveAuthoritativeElapsedMs as resolveAuthoritativeElapsedMsFromState
} from "../functions/authoritativeBattleStatePredicates";
import {
  resolveAuthoritativeRuntimeBattleId as resolveAuthoritativeRuntimeBattleIdFromState,
  resolveBattleRuntimeBattleId,
  resolveBattleRuntimeMapId
} from "../functions/battleRuntimeIdentityResolvers";
import { isVisitorBattleIdentity } from "../functions/isVisitorBattleIdentity";
import { resolveBattleSessionOwner } from "../functions/resolveBattleSessionOwner";
import { createBattleAuthoritativeBridgeController } from "../functions/createBattleAuthoritativeBridgeController";
import { BATTLE_PLAY_MODE_OPTIONS } from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import { VISITOR_BATTLE_BLOCKED_MESSAGE } from "../objects/BattlePageRuntimeConfig";
import { createBattlePageCommandController } from "../functions/createBattlePageCommandController";
import { createBattlePageEffectScopeController } from "../functions/createBattlePageEffectScopeController";


/** 中文名：使用战斗pageruntime（useBattlePageRuntime）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function useBattlePageRuntime() {
  const {
    runtimeRootRef,
    hudRootRef,
    runtimeHandleRef,
    finalizedRef,
    battleStartLockedRef,
    discardSessionOnNextTeardownRef,
    newBattleResetPendingRef,
    lastUrlRequestedNewBattleRef,
    queueStateRef,
    localAuthoritativePlayerIdRef,
    replayFramesRef,
    lastReplaySampleFrameRef,
    lastReplaySampleElapsedRef,
    lastActiveSessionPersistedAtRef,
    battleIdRef,
    activeSessionEpochRef,
    authoritativeBattleStateRef,
    authoritativeInputCaptureRef,
    authoritativeStateRequestInFlightRef,
    authoritativeCommandRequestInFlightRef,
    authoritativeCommandUplinkPendingRef,
    authoritativeCommandSeqRef,
    authoritativeCommandHistoryRef,
    authoritativeFinalizationInFlightRef,
    sharedAuthoritativeRuntimeRef,
    authoritativeFirstFrameAppliedRef,
    authoritativePreparedSkillRef,
    backendQueueJoinPendingRef
  } = useBattlePageRuntimeRefs();
  const {
    countdownStartedAtRef,
    matchWaitDeadlineRef,
    battleDurationDeadlineRef,
    countdownTimerRef,
    matchStartTimerRef,
    queuePollingTimerRef,
    roomPresenceTimerRef,
    snapshotTimerRef,
    battleEndTimerRef,
    battleDurationTimerRef,
    clearCountdownTimer,
    clearMatchStartTimer,
    clearQueuePollingTimer,
    clearRoomPresenceTimer,
    clearSnapshotTimer,
    clearBattleEndTimer,
    clearBattleDurationTimer
  } = useBattlePageTimers();
  const {
    statePollTimerRef: authoritativeStatePollTimerRef,
    stateStreamCloseRef: authoritativeStateStreamCloseRef,
    commandUplinkTimerRef: authoritativeCommandUplinkTimerRef,
    clearAuthoritativeBridgeTimers
  } = useBattleAuthoritativeBridgeTimers();

  const {
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
  } = useBattlePageViewState();
  const { transientNotice, showTransientNotice, clearTransientNotice } = useBattleTransientNotice();
  const pageData = useBattlePageData({ matchPhase, matchNonce });
  const { currentUser, loadout } = pageData;
  const currentBattleSessionOwner: ActiveBattleSessionOwner = resolveBattleSessionOwner({
    authenticatedHandle: currentUser?.handle,
    authenticatedSessionToken: currentUser?.sessionToken,
    loadoutHandle: loadout.handle
  });
  const isBattleEntryBlocked = isVisitorBattleIdentity(currentBattleSessionOwner);
  const {
    requestsNewBattle: urlRequestsNewBattle,
    requestsResumeBattle: urlRequestsResumeBattle
  } = useBattlePageUrlIntent({
    lastUrlRequestedNewBattleRef,
    newBattleResetPendingRef
  });

  useEffect(() => {
    if (!runtimeRootRef.current || !hudRootRef.current) {
      return;
    }

    const shouldStartNewBattle = newBattleResetPendingRef.current || urlRequestsNewBattle;
    const shouldRestoreActiveSession = urlRequestsResumeBattle && !shouldStartNewBattle;

    const effectScope = createBattlePageEffectScopeController({
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
    });
    effectScope.resetRuntimeScope();
    const entryBlockCleanup = effectScope.applyEntryBlock({
      blocked: isBattleEntryBlocked,
      message: VISITOR_BATTLE_BLOCKED_MESSAGE
    });
    if (entryBlockCleanup) {
      return entryBlockCleanup;
    }

    let { completedSession, restoredActiveSession } = initializeBattlePageSessionProgress({
      owner: currentBattleSessionOwner,
      shouldStartNewBattle,
      shouldRestoreActiveSession,
      loadoutHandle: loadout.handle,
      activeSessionEpochRef,
      newBattleResetPendingRef
    });

    const resolveRuntimeBattleId = (preferredQueueState: MatchmakingQueueState | null = queueStateRef.current): string =>
      resolveBattleRuntimeBattleId({
        activeBattleId: battleIdRef.current,
        queueState: preferredQueueState
      });

    const resolveRuntimeMapId = (
      preferredQueueState: MatchmakingQueueState | null = queueStateRef.current,
      preferredAuthoritativeState: AuthoritativeBattleState | null = authoritativeBattleStateRef.current,
      restoredSession: ActiveBattleSession | null = restoredActiveSession
    ): string =>
      resolveBattleRuntimeMapId({
        authoritativeState: preferredAuthoritativeState,
        queueState: preferredQueueState,
        restoredSession,
        selectedBattleModeId
      });

    const resolveAuthoritativeRuntimeBattleId = (
      preferredQueueState: MatchmakingQueueState | null = queueStateRef.current
    ): string | null =>
      resolveAuthoritativeRuntimeBattleIdFromState({
        activeBattleId: battleIdRef.current,
        queueState: preferredQueueState
      });

    const resolveAuthoritativeElapsedMs = (state: AuthoritativeBattleState | null = authoritativeBattleStateRef.current): number | null => {
      return resolveAuthoritativeElapsedMsFromState(sharedAuthoritativeRuntimeRef.current, state);
    };

    const isAuthoritativeDurationExpired = (
      state: AuthoritativeBattleState | null = authoritativeBattleStateRef.current
    ): boolean => isAuthoritativeDurationExpiredState(sharedAuthoritativeRuntimeRef.current, state);

    const isAuthoritativeBattleFinished = (): boolean => {
      return isAuthoritativeBattleFinishedState(sharedAuthoritativeRuntimeRef.current, authoritativeBattleStateRef.current);
    };

    const withAuthoritativeClock = (snapshot: GameSnapshot): GameSnapshot => {
      const elapsedMs = resolveAuthoritativeElapsedMs();
      return elapsedMs === null ? snapshot : { ...snapshot, elapsedMs };
    };

    const readRuntimeSnapshot = (): GameSnapshot | null => {
      const snapshot = runtimeHandleRef.current?.readSnapshot() ?? null;
      return snapshot && sharedAuthoritativeRuntimeRef.current ? withAuthoritativeClock(snapshot) : snapshot;
    };

    const isRuntimeBattleComplete = (snapshot: GameSnapshot | null): snapshot is GameSnapshot => {
      if (!snapshot) {
        return false;
      }

      if (!sharedAuthoritativeRuntimeRef.current) {
        return isBattleComplete(snapshot);
      }

      return getBattleAliveHeroCount(snapshot) <= 1 || isAuthoritativeBattleFinished();
    };

    const shouldStoreRuntimeCompletedSession = (snapshot: GameSnapshot | null): snapshot is GameSnapshot => {
      if (!snapshot) {
        return false;
      }

      if (!sharedAuthoritativeRuntimeRef.current) {
        return shouldStoreCompletedBattleSession(snapshot, isBattleDurationExpired());
      }

      return false;
    };

    const shouldFinalizeRuntimeSnapshot = (
      snapshot: GameSnapshot | null,
      durationExpired: boolean,
      forceCurrentSnapshot: boolean
    ): snapshot is GameSnapshot => {
      if (!snapshot) {
        return false;
      }

      if (!sharedAuthoritativeRuntimeRef.current) {
        return shouldFinalizeBattleSnapshot(snapshot, durationExpired, forceCurrentSnapshot);
      }

      return (
        getBattleAliveHeroCount(snapshot) <= 1 ||
        durationExpired ||
        forceCurrentSnapshot ||
        isAuthoritativeBattleFinished()
      );
    };

    const shouldFinalizeRuntimeSnapshotOnExit = (snapshot: GameSnapshot | null): snapshot is GameSnapshot => {
      if (!sharedAuthoritativeRuntimeRef.current) {
        return shouldFinalizeBattleSnapshotOnExit(snapshot, isBattleDurationExpired());
      }

      return shouldStoreRuntimeCompletedSession(snapshot);
    };

    const isBattleDurationExpired = (): boolean => {
      if (sharedAuthoritativeRuntimeRef.current) {
        return isAuthoritativeDurationExpired();
      }

      const deadline = battleDurationDeadlineRef.current;
      return deadline !== null && performance.now() >= deadline;
    };

    const queueRuntime: BattleMatchmakingQueueRuntimeState = {
      ticketId: null,
      cancelled: false
    };

    const resolveLocalAuthoritativePlayerId = (
      preferredQueueState: MatchmakingQueueState | null = queueStateRef.current
    ): string => {
      const retainedPlayerId = localAuthoritativePlayerIdRef.current?.trim();
      if (retainedPlayerId) {
        return retainedPlayerId;
      }

      const statePlayerId = preferredQueueState?.playerId.trim();
      if (statePlayerId) {
        localAuthoritativePlayerIdRef.current = statePlayerId;
        return statePlayerId;
      }

      return "";
    };

    const resolveLocalAuthoritativeTicketId = (): string => {
      const retainedTicketId = queueRuntime.ticketId?.trim();
      if (retainedTicketId) {
        return retainedTicketId;
      }

      return queueStateRef.current?.ticketId.trim() ?? "";
    };

    let finalizeRuntimeDelegate: (
      forceTimeLimit?: boolean,
      forceCurrentSnapshot?: boolean,
      preserveCompletedSession?: boolean
    ) => void = () => {};
    const authoritativeBridge = createBattleAuthoritativeBridgeController({
      runtimeRootRef,
      runtimeHandleRef,
      finalizedRef,
      battleIdRef,
      sharedAuthoritativeRuntimeRef,
      authoritativeFirstFrameAppliedRef,
      authoritativeBattleStateRef,
      authoritativeInputCaptureRef,
      authoritativeStateRequestInFlightRef,
      authoritativeCommandRequestInFlightRef,
      authoritativeCommandUplinkPendingRef,
      authoritativeCommandSeqRef,
      authoritativeCommandHistoryRef,
      authoritativePreparedSkillRef,
      authoritativeStatePollTimerRef,
      authoritativeStateStreamCloseRef,
      authoritativeCommandUplinkTimerRef,
      clearAuthoritativeBridgeTimers,
      resolveLocalAuthoritativePlayerId,
      resolveLocalAuthoritativeTicketId,
      isAuthoritativeBattleFinished,
      isAuthoritativeDurationExpired,
      finalizeRuntime: (...args) => {
        finalizeRuntimeDelegate(...args);
      },
      showTransientNotice
    });

    const runtimeLifecycle = createBattleRuntimeLifecycleController({
      owner: currentBattleSessionOwner,
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
      stopAuthoritativeBattleBridge: authoritativeBridge.stopAuthoritativeBattleBridge,
      readRuntimeSnapshot,
      resolveRuntimeBattleId,
      resolveRuntimeMapId,
      resolveLocalAuthoritativePlayerId,
      resolveLocalAuthoritativeTicketId,
      shouldStoreRuntimeCompletedSession,
      shouldFinalizeRuntimeSnapshot,
      shouldFinalizeRuntimeSnapshotOnExit,
      isBattleDurationExpired,
      isQueueJoinCancelled: () => queueRuntime.cancelled,
      applyRecoveredAuthoritativeIdentity: (identity) => {
        if (identity.localAuthoritativePlayerId) {
          localAuthoritativePlayerIdRef.current = identity.localAuthoritativePlayerId;
        }
        if (identity.localAuthoritativeTicketId) {
          queueRuntime.ticketId = identity.localAuthoritativeTicketId;
        }
      },
      startBattleRuntime: (...args) => {
        startBattleRuntime(...args);
      },
      setMatchPhase,
      setCurrentResultSummary,
      setCurrentReplayId,
      setMatchNonce
    });
    finalizeRuntimeDelegate = runtimeLifecycle.finalizeRuntime;
    const {
      finalizeRuntime,
      persistRuntime,
      pushReplayFrame,
      recoverCompletedSession,
      shouldFinalizeRuntimeOnExit,
      tearDownRuntime
    } = runtimeLifecycle;

    const startBattleRuntime = createBattleRuntimeLaunchController({
      runtimeRootRef,
      hudRootRef,
      runtimeHandleRef,
      finalizedRef,
      queueStateRef,
      sharedAuthoritativeRuntimeRef,
      authoritativeFirstFrameAppliedRef,
      authoritativePreparedSkillRef,
      replayFramesRef,
      lastReplaySampleFrameRef,
      lastReplaySampleElapsedRef,
      battleIdRef,
      battleDurationDeadlineRef,
      snapshotTimerRef,
      battleEndTimerRef,
      battleDurationTimerRef,
      readRestoredActiveSession: () => restoredActiveSession,
      resolveLocalAuthoritativePlayerId,
      resolveRuntimeBattleId,
      resolveRuntimeMapId,
      readRuntimeSnapshot,
      pushReplayFrame,
      persistRuntime,
      isRuntimeBattleComplete,
      isBattleDurationExpired,
      isAuthoritativeDurationExpired,
      finalizeRuntime,
      authoritativeBridge,
      setActiveDrawer,
      setMatchPhase
    });

    const removePageExitPersistence = createBattlePageExitPersistenceController({
      owner: currentBattleSessionOwner,
      discardSessionOnNextTeardownRef,
      newBattleResetPendingRef,
      finalizedRef,
      shouldFinalizeRuntimeOnExit,
      isBattleDurationExpired,
      finalizeRuntime,
      persistRuntime
    });

    if (completedSession) {
      recoverCompletedSession(completedSession);
      completedSession = null;
      return () => {
        removePageExitPersistence();
        queueRuntime.cancelled = true;
        backendQueueJoinPendingRef.current = false;
        tearDownRuntime();
      };
    }

    const {
      leaveJoinedQueueIfIdle
    } = createBattleMatchmakingStartupController({
      owner: currentBattleSessionOwner,
      loadout,
      selectedBattleModeId,
      queueRuntime,
      queueStateRef,
      localAuthoritativePlayerIdRef,
      backendQueueJoinPendingRef,
      battleStartLockedRef,
      finalizedRef,
      activeSessionEpochRef,
      countdownStartedAtRef,
      matchWaitDeadlineRef,
      countdownTimerRef,
      matchStartTimerRef,
      queuePollingTimerRef,
      roomPresenceTimerRef,
      readRestoredActiveSession: () => restoredActiveSession,
      setRestoredActiveSession: (session) => {
        restoredActiveSession = session;
      },
      resolveAuthoritativeRuntimeBattleId,
      resolveRuntimeBattleId,
      startBattleRuntime,
      clearMatchStartTimer,
      clearCountdownTimer,
      clearQueuePollingTimer,
      clearRoomPresenceTimer,
      setMatchPhase,
      setMatchCountdownMs,
      setQueueState,
      setSelectedBattleModeId
    });

    return () => {
      removePageExitPersistence();
      queueRuntime.cancelled = true;
      backendQueueJoinPendingRef.current = false;
      clearCountdownTimer();
      clearMatchStartTimer();
      clearQueuePollingTimer();
      clearRoomPresenceTimer();
      leaveJoinedQueueIfIdle();
      const shouldDiscardSession = discardSessionOnNextTeardownRef.current;
      discardSessionOnNextTeardownRef.current = false;
      if (shouldDiscardSession || newBattleResetPendingRef.current) {
        clearActiveBattleSession(currentBattleSessionOwner);
        tearDownRuntime();
        return;
      }
      const shouldFinalizeOnExit = shouldFinalizeRuntimeOnExit();
      finalizeRuntime(isBattleDurationExpired(), false, shouldFinalizeOnExit);
      if (!finalizedRef.current) {
        persistRuntime(true);
      }
      tearDownRuntime();
      clearTransientNotice();
    };
  }, [
    clearAuthoritativeBridgeTimers,
    clearTransientNotice,
    currentBattleSessionOwner.handle,
    currentBattleSessionOwner.sessionToken,
    isBattleEntryBlocked,
    loadout.handle,
    matchNonce,
    selectedBattleModeId,
    showTransientNotice,
    urlRequestsNewBattle,
    urlRequestsResumeBattle
  ]);

  const {
    selectBattleMode,
    startNewMatch
  } = createBattlePageCommandController({
    owner: currentBattleSessionOwner,
    matchPhase,
    selectedBattleModeId,
    entryBlocked: isBattleEntryBlocked,
    entryBlockedMessage: VISITOR_BATTLE_BLOCKED_MESSAGE,
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
  });

  return {
    runtimeRootRef,
    hudRootRef,
    matchPhase,
    matchCountdownMs,
    currentResultSummary,
    currentReplayId,
    queueState,
    activeDrawer,
    transientNotice,
    entryBlockNotice,
    selectedBattleModeId,
    battleModeOptions: BATTLE_PLAY_MODE_OPTIONS,
    ...pageData,
    selectBattleMode,
    openDrawer: setActiveDrawer,
    closeDrawer: () => setActiveDrawer(null),
    startNewMatch
  };
}
