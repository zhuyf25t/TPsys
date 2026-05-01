import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import type { GameSnapshot, PlayerCommand, PreparedSkill, Vec2 } from "../../../domain/types";
import {
  loadAuthoritativeBattleState,
  openAuthoritativeBattleStateStream,
  sendAuthoritativeBattleCommand,
  type AuthoritativeBattleState
} from "../adapters/authoritativeBattleClient";
import {
  BATTLE_MATCH_DURATION_MS,
  finalizeLocalBattle,
  type LocalBattleReturnSummary
} from "../local/battleLocalGateway";
import { createBattleRuntime, type BattleRuntimeHandle } from "../renderer/createBattleRuntime";
import {
  getBattleAliveHeroCount,
  isBattleComplete
} from "../runtime-local/session/battleCompletion";
import { appendBotOnlyBattleClosureReplayFrames } from "../runtime-local/session/battleFinalizationReplay";
import type { InitialBattleParticipantsConfig } from "../runtime-local/session/initialBattleSnapshot";
import {
  clearActiveBattleSession,
  clearActiveBattleSessionProgress,
  consumeCompletedActiveBattleSession,
  publishActiveBattleSessionEpoch,
  readActiveBattleSession,
  writeActiveBattleSession,
  writeCompletedActiveBattleSession
} from "./activeBattleSessionStore";
import {
  MATCHMAKING_DURATION_MS,
  type ActiveBattleSession,
  type ActiveBattleSessionOwner,
  type BattleDrawerId,
  type MatchPhase
} from "./battlePageTypes";
import {
  joinMatchmakingQueue,
  leaveMatchmakingQueue,
  loadMatchmakingQueueStatus,
  refreshMatchmakingRoomPresence
} from "./matchmakingQueueGateway";
import { type MatchmakingQueueState } from "./matchmakingQueueTypes";
import {
  hasSharedBattleSession,
  resolveSharedQueueRemainingMs
} from "./multiplayerRoomTiming";
import { buildReplayFrame, REPLAY_SAMPLE_INTERVAL_MS, shouldCaptureReplayFrame } from "../../replay/replayRecorder";
import type { ReplayFrame } from "../../replay/replayTypes";
import { useBattlePageData } from "./useBattlePageData";
import {
  BATTLE_COMPLETION_CHECK_INTERVAL_MS,
  buildInitialBattleParticipants,
  createExitedBattleSnapshot,
  createLocalBattleId,
  isActiveBattleSessionCompatibleWithQueueState,
  isActiveBattleSessionForLocalPlayer,
  MATCH_START_RECHECK_MS,
  requiresAuthoritativeBattleId,
  resolveBackendBattleId,
  resolveBattleFinalizationSnapshot,
  shouldFinalizeBattleSnapshot,
  shouldFinalizeBattleSnapshotOnExit,
  shouldStoreCompletedBattleSession,
  START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS
} from "./battlePageRuntimeHelpers";
import {
  createAuthoritativeBattleInputCapture,
  type AuthoritativeBattleInputCapture,
  type AuthoritativeBattleInputSnapshot
} from "./authoritativeBattleInput";
import { createAuthoritativeCommandHistory } from "./authoritativeCommandHistory";
import { toAuthoritativeResultSummary } from "./authoritativeResultSummary";
import { loadBattleResultByBattleId } from "../results/battleResultsApi";
import { useBattlePageTimers } from "./useBattlePageTimers";
import {
  resolveAcceptedCommandNotice,
  resolveCommandFailureNotice
} from "./authoritativeCommandNotice";
import { isBattleVisitorHandle } from "../rules/battleRules";

const AUTHORITATIVE_STATE_POLL_INTERVAL_MS = 33;
const AUTHORITATIVE_COMMAND_UPLINK_INTERVAL_MS = 33;
const AUTHORITATIVE_BOOTSTRAP_RETRY_MS = 150;
const AUTHORITATIVE_RESULT_READY_RETRY_MS = 250;
const AUTHORITATIVE_RESULT_READY_TIMEOUT_MS = 5_000;
const ACTIVE_SESSION_PERSIST_INTERVAL_MS = 5_000;
const BATTLE_COMMAND_NOTICE_DEDUPE_MS = 1_200;
const BATTLE_COMMAND_NOTICE_VISIBLE_MS = 2_000;
const VISITOR_BATTLE_BLOCKED_MESSAGE = "请先登录正式账号，Visitor/未登录状态不能进入正式匹配或开战。";

export interface BattlePageTransientNotice {
  id: number;
  message: string;
}

export function useBattlePageRuntime() {
  const location = useLocation();
  const runtimeRootRef = useRef<HTMLDivElement | null>(null);
  const hudRootRef = useRef<HTMLDivElement | null>(null);
  const runtimeHandleRef = useRef<BattleRuntimeHandle | null>(null);
  const finalizedRef = useRef(false);
  const battleStartLockedRef = useRef(false);
  const discardSessionOnNextTeardownRef = useRef(false);
  const newBattleResetPendingRef = useRef(false);
  const lastUrlRequestedNewBattleRef = useRef(false);
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
  const queueStateRef = useRef<MatchmakingQueueState | null>(null);
  const localAuthoritativePlayerIdRef = useRef<string | null>(null);
  const replayFramesRef = useRef<ReplayFrame[]>([]);
  const lastReplaySampleFrameRef = useRef<ReplayFrame | null>(null);
  const lastReplaySampleElapsedRef = useRef<number | null>(null);
  const lastActiveSessionPersistedAtRef = useRef(0);
  const battleIdRef = useRef<string | null>(null);
  const activeSessionEpochRef = useRef<string | null>(null);
  const authoritativeBattleStateRef = useRef<AuthoritativeBattleState | null>(null);
  const authoritativeInputCaptureRef = useRef<AuthoritativeBattleInputCapture | null>(null);
  const authoritativeStateRequestInFlightRef = useRef(false);
  const authoritativeCommandRequestInFlightRef = useRef(false);
  const authoritativeCommandUplinkPendingRef = useRef(false);
  const authoritativeCommandSeqRef = useRef(0);
  const authoritativeCommandHistoryRef = useRef(createAuthoritativeCommandHistory());
  const authoritativeStatePollTimerRef = useRef<number | null>(null);
  const authoritativeStateStreamCloseRef = useRef<(() => void) | null>(null);
  const authoritativeCommandUplinkTimerRef = useRef<number | null>(null);
  const authoritativeFinalizationInFlightRef = useRef(false);
  const sharedAuthoritativeRuntimeRef = useRef(false);
  const authoritativeFirstFrameAppliedRef = useRef(false);
  const authoritativePreparedSkillRef = useRef<PreparedSkill>(null);
  const backendQueueJoinPendingRef = useRef(false);
  const transientNoticeTimerRef = useRef<number | null>(null);
  const transientNoticeLastShownRef = useRef<{ message: string; shownAt: number } | null>(null);
  const transientNoticeIdRef = useRef(0);

  const [matchNonce, setMatchNonce] = useState(0);
  const [matchCountdownMs, setMatchCountdownMs] = useState(MATCHMAKING_DURATION_MS);
  const [currentResultSummary, setCurrentResultSummary] = useState<LocalBattleReturnSummary | null>(null);
  const [currentReplayId, setCurrentReplayId] = useState<string | null>(null);
  const [matchPhase, setMatchPhase] = useState<MatchPhase>("matching");
  const [queueState, setQueueState] = useState<MatchmakingQueueState | null>(null);
  const [activeDrawer, setActiveDrawer] = useState<BattleDrawerId | null>(null);
  const [transientNotice, setTransientNotice] = useState<BattlePageTransientNotice | null>(null);
  const [entryBlockNotice, setEntryBlockNotice] = useState<string | null>(null);
  const pageData = useBattlePageData({ matchPhase, matchNonce });
  const { currentUser, loadout } = pageData;
  const currentBattleSessionOwner: ActiveBattleSessionOwner = {
    handle: currentUser?.handle ?? loadout.handle,
    sessionToken: currentUser?.sessionToken?.trim() ? currentUser.sessionToken : null
  };
  const isBattleEntryBlocked = isVisitorBattleIdentity(currentBattleSessionOwner);
  const urlSearchParams = new URLSearchParams(location.search);
  const urlRequestsNewBattle = urlSearchParams.get("new") === "1";
  const urlRequestsResumeBattle = urlSearchParams.get("resume") === "1" && !urlRequestsNewBattle;
  if (urlRequestsNewBattle && !lastUrlRequestedNewBattleRef.current) {
    newBattleResetPendingRef.current = true;
  }
  lastUrlRequestedNewBattleRef.current = urlRequestsNewBattle;

  useEffect(() => {
    if (!runtimeRootRef.current || !hudRootRef.current) {
      return;
    }

    const shouldStartNewBattle = newBattleResetPendingRef.current || urlRequestsNewBattle;
    const shouldRestoreActiveSession = urlRequestsResumeBattle && !shouldStartNewBattle;

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
    runtimeRootRef.current.replaceChildren();
    hudRootRef.current.replaceChildren();

    if (isBattleEntryBlocked) {
      setEntryBlockNotice(VISITOR_BATTLE_BLOCKED_MESSAGE);
      setMatchPhase("matching");
      setQueueState(null);
      return () => {
        clearTransientNotice();
        runtimeRootRef.current?.replaceChildren();
        hudRootRef.current?.replaceChildren();
      };
    }

    setEntryBlockNotice(null);

    let completedSession: ActiveBattleSession | null = null;
    let restoredActiveSession: ActiveBattleSession | null = null;

    if (shouldStartNewBattle) {
      activeSessionEpochRef.current = publishActiveBattleSessionEpoch(currentBattleSessionOwner);
      clearActiveBattleSession(currentBattleSessionOwner);
      newBattleResetPendingRef.current = false;
    } else {
      completedSession = consumeCompletedActiveBattleSession(currentBattleSessionOwner);
    }

    if (!completedSession) {
      const activeSession = readActiveBattleSession(currentBattleSessionOwner);
      completedSession = consumeCompletedActiveBattleSession(currentBattleSessionOwner);
      if (
        !completedSession &&
        activeSession &&
        shouldRestoreActiveSession &&
        isActiveBattleSessionForLocalPlayer(activeSession, loadout.handle)
      ) {
        restoredActiveSession = activeSession;
        activeSessionEpochRef.current = activeSession.sessionEpoch ?? null;
      } else if (!completedSession && activeSession) {
        activeSessionEpochRef.current = publishActiveBattleSessionEpoch(currentBattleSessionOwner);
        clearActiveBattleSessionProgress(currentBattleSessionOwner);
      }
    }

    if (!completedSession && !restoredActiveSession && activeSessionEpochRef.current === null) {
      activeSessionEpochRef.current = publishActiveBattleSessionEpoch(currentBattleSessionOwner);
    }

    const resolveRuntimeBattleId = (preferredQueueState: MatchmakingQueueState | null = queueStateRef.current): string =>
      battleIdRef.current ?? resolveBackendBattleId(preferredQueueState) ?? createLocalBattleId();

    const resolveAuthoritativeRuntimeBattleId = (
      preferredQueueState: MatchmakingQueueState | null = queueStateRef.current
    ): string | null => {
      const activeBattleId = battleIdRef.current?.trim();
      if (activeBattleId) {
        return activeBattleId;
      }

      const backendBattleId = resolveBackendBattleId(preferredQueueState);
      if (backendBattleId) {
        return backendBattleId;
      }

      if (requiresAuthoritativeBattleId(preferredQueueState)) {
        return null;
      }

      return createLocalBattleId();
    };

    const requiresAuthoritativeStartup = (
      preferredQueueState: MatchmakingQueueState | null = queueStateRef.current
    ): boolean => backendQueueJoinPendingRef.current || requiresAuthoritativeBattleId(preferredQueueState);

    const resolveAuthoritativeDurationMs = (state: AuthoritativeBattleState | null = authoritativeBattleStateRef.current): number | null => {
      if (!sharedAuthoritativeRuntimeRef.current || !state || !Number.isFinite(state.durationMs)) {
        return null;
      }

      return Math.max(1, Math.round(state.durationMs));
    };

    const resolveAuthoritativeElapsedMs = (state: AuthoritativeBattleState | null = authoritativeBattleStateRef.current): number | null => {
      const durationMs = resolveAuthoritativeDurationMs(state);
      if (durationMs === null || !state || !Number.isFinite(state.elapsedMs)) {
        return null;
      }

      return Math.max(0, Math.min(Math.round(state.elapsedMs), durationMs));
    };

    const isAuthoritativeDurationExpired = (
      state: AuthoritativeBattleState | null = authoritativeBattleStateRef.current
    ): boolean => {
      const durationMs = resolveAuthoritativeDurationMs(state);
      const elapsedMs = resolveAuthoritativeElapsedMs(state);
      return durationMs !== null && elapsedMs !== null && elapsedMs >= durationMs;
    };

    const isAuthoritativeBattleFinished = (): boolean => {
      const state = authoritativeBattleStateRef.current;
      return Boolean(
        sharedAuthoritativeRuntimeRef.current &&
          state &&
          (state.phase === "finished" || isAuthoritativeDurationExpired(state))
      );
    };

    const isAuthoritativeStateRecoverable = (
      state: AuthoritativeBattleState | null,
      expectedBattleId: string
    ): state is AuthoritativeBattleState => {
      if (!state || state.battleId.trim() !== expectedBattleId.trim()) {
        return false;
      }

      if (state.phase === "finished") {
        return false;
      }

      if (!Number.isFinite(state.durationMs) || !Number.isFinite(state.elapsedMs)) {
        return false;
      }

      return Math.round(state.elapsedMs) < Math.max(1, Math.round(state.durationMs));
    };

    const showTransientNotice = (message: string): void => {
      const now = performance.now();
      const lastShown = transientNoticeLastShownRef.current;
      if (lastShown?.message === message && now - lastShown.shownAt < BATTLE_COMMAND_NOTICE_DEDUPE_MS) {
        return;
      }

      transientNoticeLastShownRef.current = { message, shownAt: now };
      transientNoticeIdRef.current += 1;
      setTransientNotice({ id: transientNoticeIdRef.current, message });
      if (transientNoticeTimerRef.current !== null) {
        window.clearTimeout(transientNoticeTimerRef.current);
      }
      transientNoticeTimerRef.current = window.setTimeout(() => {
        transientNoticeTimerRef.current = null;
        setTransientNotice(null);
      }, BATTLE_COMMAND_NOTICE_VISIBLE_MS);
    };

    const isAuthoritativeFinalResultReady = (state: AuthoritativeBattleState | null): state is AuthoritativeBattleState =>
      Boolean(state && state.phase === "finished" && state.resultReady && state.replayReady);

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

    let queueTicketId: string | null = null;
    let queueJoinCancelled = false;
    const queueRequestId = createQueueRequestId();

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
      const retainedTicketId = queueTicketId?.trim();
      if (retainedTicketId) {
        return retainedTicketId;
      }

      return queueStateRef.current?.ticketId.trim() ?? "";
    };

    const ensureAuthoritativeInputCapture = (): void => {
      if (!authoritativeInputCaptureRef.current && runtimeRootRef.current) {
        authoritativeInputCaptureRef.current = createAuthoritativeBattleInputCapture({
          resolveRuntimeRoot: () => runtimeRootRef.current,
          resolvePlayerPosition: () =>
            resolveAuthoritativePlayerPosition(authoritativeBattleStateRef.current, resolveLocalAuthoritativePlayerId())
        });
      }
    };

    const clearAuthoritativeBridgeTimers = (): void => {
      if (authoritativeStatePollTimerRef.current !== null) {
        window.clearInterval(authoritativeStatePollTimerRef.current);
        authoritativeStatePollTimerRef.current = null;
      }
      authoritativeStateStreamCloseRef.current?.();
      authoritativeStateStreamCloseRef.current = null;
      if (authoritativeCommandUplinkTimerRef.current !== null) {
        window.clearInterval(authoritativeCommandUplinkTimerRef.current);
        authoritativeCommandUplinkTimerRef.current = null;
      }
    };

    const setAuthoritativePreparedSkill = (preparedSkill: PreparedSkill): void => {
      authoritativePreparedSkillRef.current = preparedSkill;
      runtimeHandleRef.current?.setAuthoritativePreparedSkill(preparedSkill);
    };

    const applyAuthoritativeBattleState = (state: AuthoritativeBattleState): void => {
      if (finalizedRef.current) {
        return;
      }

      authoritativeBattleStateRef.current = state;
      battleIdRef.current = state.battleId;
      pruneAuthoritativeCommandHistoryFromState(state);
      const applied =
        runtimeHandleRef.current?.applyAuthoritativeState(
          state,
          resolveLocalAuthoritativePlayerId(),
          authoritativeCommandHistoryRef.current.entries
        ) ?? false;
      if (applied) {
        authoritativeFirstFrameAppliedRef.current = true;
      }
      if (authoritativeFirstFrameAppliedRef.current && isAuthoritativeBattleFinished()) {
        finalizeRuntime(isAuthoritativeDurationExpired());
      }
    };

    const stopAuthoritativeBattleBridge = (): void => {
      clearAuthoritativeBridgeTimers();
      setAuthoritativePreparedSkill(null);
      authoritativeBattleStateRef.current = null;
      authoritativeStateRequestInFlightRef.current = false;
      authoritativeCommandRequestInFlightRef.current = false;
      authoritativeCommandUplinkPendingRef.current = false;
      authoritativeCommandHistoryRef.current.clear();
      authoritativeInputCaptureRef.current?.destroy();
      authoritativeInputCaptureRef.current = null;
    };

    const pruneAuthoritativeCommandHistoryFromState = (state: AuthoritativeBattleState): void => {
      const playerId = resolveLocalAuthoritativePlayerId();
      if (!playerId) {
        return;
      }

      const localPlayer = state.players.find((player) => player.playerId === playerId);
      if (localPlayer) {
        authoritativeCommandSeqRef.current = Math.max(
          authoritativeCommandSeqRef.current,
          localPlayer.lastClientCommandSeq
        );
        authoritativeCommandHistoryRef.current.pruneThrough(localPlayer.lastClientCommandSeq);
      }
    };

    const pollAuthoritativeBattleState = async (): Promise<void> => {
      const battleId = battleIdRef.current?.trim();
      if (!sharedAuthoritativeRuntimeRef.current || !battleId || authoritativeStateRequestInFlightRef.current) {
        return;
      }

      authoritativeStateRequestInFlightRef.current = true;
      try {
        const state = await loadAuthoritativeBattleState(battleId);
        if (!state || finalizedRef.current) {
          return;
        }

        applyAuthoritativeBattleState(state);
      } finally {
        authoritativeStateRequestInFlightRef.current = false;
      }
    };

    const startAuthoritativeStatePolling = (): void => {
      if (authoritativeStatePollTimerRef.current !== null) {
        return;
      }

      void pollAuthoritativeBattleState();
      authoritativeStatePollTimerRef.current = window.setInterval(() => {
        void pollAuthoritativeBattleState();
      }, AUTHORITATIVE_STATE_POLL_INTERVAL_MS);
    };

    const uplinkAuthoritativeBattleCommand = async (): Promise<void> => {
      const battleId = battleIdRef.current?.trim();
      const playerId = resolveLocalAuthoritativePlayerId();
      const ticketId = resolveLocalAuthoritativeTicketId();
      const inputCapture = authoritativeInputCaptureRef.current;
      if (authoritativeCommandRequestInFlightRef.current) {
        authoritativeCommandUplinkPendingRef.current = true;
        return;
      }

      if (
        !sharedAuthoritativeRuntimeRef.current ||
        !battleId ||
        !playerId ||
        !ticketId ||
        !inputCapture ||
        isAuthoritativeBattleFinished()
      ) {
        return;
      }

      const fallbackCommand = inputCapture.readSnapshot();
      const runtimeCommand = sharedAuthoritativeRuntimeRef.current
        ? runtimeHandleRef.current?.readPlayerCommand() ?? null
        : null;
      const command = runtimeCommand
        ? resolveAuthoritativePreparedInput(runtimeCommand, fallbackCommand, authoritativePreparedSkillRef.current)
        : resolveAuthoritativePreparedInput(null, fallbackCommand, authoritativePreparedSkillRef.current);
      const clientCommandSeq = authoritativeCommandSeqRef.current + 1;
      authoritativeCommandSeqRef.current = clientCommandSeq;
      setAuthoritativePreparedSkill(command.preparedSkill);
      const outboundCommand = {
        battleId,
        playerId,
        ticketId,
        clientTick: authoritativeBattleStateRef.current?.tick ?? 0,
        clientCommandSeq,
        movement: command.input.movement,
        aim: command.input.aim,
        primaryHeld: command.input.primaryHeld,
        sprint: command.input.sprint,
        reloadPressed: command.input.reloadPressed,
        castDash: command.input.castDash,
        castBlink: command.input.castBlink,
        castFreeze: command.input.castFreeze,
        pointerWorld: command.confirmedTarget ?? command.input.pointerWorld,
        switchWeaponDirection: command.input.switchWeaponDirection,
        switchWeaponIndex: command.input.switchWeaponIndex
      };
      authoritativeCommandHistoryRef.current.record(outboundCommand);
      authoritativeCommandRequestInFlightRef.current = true;
      try {
        const outcome = await sendAuthoritativeBattleCommand(outboundCommand);
        if (outcome.ok) {
          const { accepted } = outcome;
          authoritativeCommandSeqRef.current = Math.max(
            authoritativeCommandSeqRef.current,
            accepted.acceptedCommandSeq
          );
          const acceptedNotice = resolveAcceptedCommandNotice(accepted);
          if (acceptedNotice) {
            showTransientNotice(acceptedNotice);
          }
        } else {
          showTransientNotice(resolveCommandFailureNotice(outcome));
        }
      } finally {
        authoritativeCommandRequestInFlightRef.current = false;
        if (authoritativeCommandUplinkPendingRef.current && sharedAuthoritativeRuntimeRef.current && !finalizedRef.current) {
          authoritativeCommandUplinkPendingRef.current = false;
          window.setTimeout(() => {
            void uplinkAuthoritativeBattleCommand();
          }, 0);
        }
      }
    };

    const startAuthoritativeBattleBridge = (): void => {
      ensureAuthoritativeInputCapture();
      clearAuthoritativeBridgeTimers();
      const battleId = battleIdRef.current?.trim();
      const stream = battleId
        ? openAuthoritativeBattleStateStream(battleId, {
            onState: applyAuthoritativeBattleState,
            onFallback: () => {
              authoritativeStateStreamCloseRef.current = null;
              if (!finalizedRef.current && sharedAuthoritativeRuntimeRef.current) {
                startAuthoritativeStatePolling();
              }
            }
          })
        : null;

      if (stream) {
        authoritativeStateStreamCloseRef.current = stream.close;
      } else {
        startAuthoritativeStatePolling();
      }

      void uplinkAuthoritativeBattleCommand();
      authoritativeCommandUplinkTimerRef.current = window.setInterval(() => {
        void uplinkAuthoritativeBattleCommand();
      }, AUTHORITATIVE_COMMAND_UPLINK_INTERVAL_MS);
    };

    const buildActiveSession = (snapshot: GameSnapshot): ActiveBattleSession => {
      const battleId = resolveRuntimeBattleId();
      battleIdRef.current = battleId;
      const localAuthoritativePlayerId = resolveLocalAuthoritativePlayerId();
      const localAuthoritativeTicketId = resolveLocalAuthoritativeTicketId();

      return {
        version: 1,
        owner: currentBattleSessionOwner,
        ...(activeSessionEpochRef.current ? { sessionEpoch: activeSessionEpochRef.current } : {}),
        battleId,
        ...(sharedAuthoritativeRuntimeRef.current ? { sharedAuthoritativeRuntime: true } : {}),
        ...(sharedAuthoritativeRuntimeRef.current && localAuthoritativePlayerId
          ? { localAuthoritativePlayerId }
          : {}),
        ...(sharedAuthoritativeRuntimeRef.current && localAuthoritativeTicketId
          ? { localAuthoritativeTicketId }
          : {}),
        savedAt: Date.now(),
        snapshot,
        replayFrames: replayFramesRef.current,
        lastReplaySampleElapsed: lastReplaySampleElapsedRef.current
      };
    };

    const persistRuntime = (forceReplayFrame = false, snapshotOverride: GameSnapshot | null = null): void => {
      const runtime = runtimeHandleRef.current;
      if (!runtime || finalizedRef.current) {
        return;
      }

      if (sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current) {
        return;
      }

      const snapshot = snapshotOverride ?? readRuntimeSnapshot();
      if (!snapshot) {
        return;
      }

      if (forceReplayFrame) {
        pushReplayFrame(snapshot, true);
      }

      if (shouldStoreRuntimeCompletedSession(snapshot)) {
        writeCompletedActiveBattleSession(buildActiveSession(snapshot));
        return;
      }

      const now = Date.now();
      if (!forceReplayFrame && now - lastActiveSessionPersistedAtRef.current < ACTIVE_SESSION_PERSIST_INTERVAL_MS) {
        return;
      }

      writeActiveBattleSession(buildActiveSession(snapshot));
      lastActiveSessionPersistedAtRef.current = now;
    };

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
      clearActiveBattleSession(currentBattleSessionOwner);
      finalizedRef.current = true;
      tearDownRuntime();
      setMatchPhase("matching");
      setCurrentResultSummary(null);
      setCurrentReplayId(null);
      setMatchNonce((value) => value + 1);
    };

    const pushReplayFrame = (snapshot: GameSnapshot, force = false): void => {
      if (!force && !shouldCaptureReplayFrame(lastReplaySampleElapsedRef.current, snapshot.elapsedMs)) {
        return;
      }

      const nextFrame = buildReplayFrame(snapshot);
      const lastFrameIndex = replayFramesRef.current.length - 1;
      if (lastFrameIndex >= 0 && replayFramesRef.current[lastFrameIndex].elapsedMs === nextFrame.elapsedMs) {
        replayFramesRef.current[lastFrameIndex] = nextFrame;
      } else {
        replayFramesRef.current.push(nextFrame);
      }
      lastReplaySampleFrameRef.current = nextFrame;
      lastReplaySampleElapsedRef.current = snapshot.elapsedMs;
    };

    const shouldFinalizeRuntimeOnExit = (): boolean => {
      const snapshot = readRuntimeSnapshot();
      return shouldFinalizeRuntimeSnapshotOnExit(snapshot);
    };

    const buildResolvedReplayFrames = (frames: ReplayFrame[], snapshot: GameSnapshot): ReplayFrame[] => {
      const { botOnlyClosure } = resolveBattleFinalizationSnapshot(snapshot, false);
      return botOnlyClosure ? appendBotOnlyBattleClosureReplayFrames(frames, botOnlyClosure) : [...frames];
    };

    const loadAuthoritativeResultSummaryWhenReady = async (
      battleId: string,
      timeoutMs = AUTHORITATIVE_RESULT_READY_TIMEOUT_MS
    ): Promise<LocalBattleReturnSummary | null> => {
      const normalizedBattleId = battleId.trim();
      if (!normalizedBattleId) {
        return null;
      }

      const deadline = performance.now() + timeoutMs;
      while (!queueJoinCancelled && performance.now() <= deadline) {
        const cachedState =
          authoritativeBattleStateRef.current?.battleId === normalizedBattleId ? authoritativeBattleStateRef.current : null;
        const state = isAuthoritativeFinalResultReady(cachedState)
          ? cachedState
          : await loadAuthoritativeBattleState(normalizedBattleId);
        if (queueJoinCancelled) {
          return null;
        }

        if (state) {
          authoritativeBattleStateRef.current = state;
        }

        if (isAuthoritativeFinalResultReady(state)) {
          const remoteRecord = await loadBattleResultByBattleId(normalizedBattleId, currentBattleSessionOwner.handle);
          if (queueJoinCancelled) {
            return null;
          }

          if (remoteRecord?.battleId.trim() === normalizedBattleId) {
            return toAuthoritativeResultSummary(remoteRecord);
          }
        }

        await new Promise((resolve) => window.setTimeout(resolve, AUTHORITATIVE_RESULT_READY_RETRY_MS));
      }

      return null;
    };

    const settleAuthoritativeResult = (summary: LocalBattleReturnSummary, battleId: string): void => {
      finalizedRef.current = true;
      clearActiveBattleSession(currentBattleSessionOwner);
      tearDownRuntime();
      setMatchPhase("settled");
      setCurrentResultSummary(summary);
      setCurrentReplayId(battleId);
    };

    const finalizeSharedAuthoritativeRuntime = async (snapshot: GameSnapshot): Promise<void> => {
      const normalizedBattleId = battleIdRef.current?.trim() ?? "";
      if (!normalizedBattleId || authoritativeFinalizationInFlightRef.current || finalizedRef.current) {
        return;
      }

      authoritativeFinalizationInFlightRef.current = true;
      writeActiveBattleSession(buildActiveSession(snapshot));
      const summary = await loadAuthoritativeResultSummaryWhenReady(normalizedBattleId);
      authoritativeFinalizationInFlightRef.current = false;
      if (queueJoinCancelled || finalizedRef.current) {
        return;
      }

      if (!summary) {
        writeActiveBattleSession(buildActiveSession(snapshot));
        return;
      }

      settleAuthoritativeResult(summary, normalizedBattleId);
    };

    const finalizeRuntime = (
      forceTimeLimit = false,
      forceCurrentSnapshot = false,
      preserveCompletedSession = false
    ): void => {
      const runtime = runtimeHandleRef.current;
      if (!runtime || finalizedRef.current) {
        return;
      }

      if (sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current) {
        return;
      }

      const snapshot = readRuntimeSnapshot();
      const durationExpired = forceTimeLimit && isBattleDurationExpired();
      if (!shouldFinalizeRuntimeSnapshot(snapshot, durationExpired, forceCurrentSnapshot)) {
        return;
      }

      const isSharedAuthoritativeRuntime = sharedAuthoritativeRuntimeRef.current;
      if (isSharedAuthoritativeRuntime) {
        void finalizeSharedAuthoritativeRuntime(snapshot);
        return;
      }

      const exitResolvedSnapshot =
        forceCurrentSnapshot && !durationExpired
          ? createExitedBattleSnapshot(snapshot)
          : snapshot;
      const { finalSnapshot, botOnlyClosure } = resolveBattleFinalizationSnapshot(exitResolvedSnapshot, durationExpired);
      replayFramesRef.current = botOnlyClosure
        ? appendBotOnlyBattleClosureReplayFrames(replayFramesRef.current, botOnlyClosure)
        : [...replayFramesRef.current];

      finalizedRef.current = true;
      pushReplayFrame(finalSnapshot, true);
      writeCompletedActiveBattleSession(buildActiveSession(finalSnapshot));

      const finalized = finalizeLocalBattleWithFreshIdFallback({
        battleId: battleIdRef.current ?? undefined,
        snapshot: finalSnapshot,
        finishedAt: Date.now(),
        thumbnailDataUrl: runtime.captureThumbnail(),
        replayFrames: replayFramesRef.current,
        botOnlyClosure,
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

      clearActiveBattleSession(currentBattleSessionOwner);
      tearDownRuntime();
      setMatchPhase("settled");

      setCurrentResultSummary(finalized.returnSummary);
      setCurrentReplayId(finalized.replay.id);
    };

    const recoverSharedAuthoritativeCompletedSession = async (session: ActiveBattleSession): Promise<void> => {
      const normalizedBattleId = session.battleId.trim();
      if (!normalizedBattleId) {
        clearActiveBattleSession(currentBattleSessionOwner);
        return;
      }

      const restoredPlayerId = session.localAuthoritativePlayerId?.trim();
      const restoredTicketId = session.localAuthoritativeTicketId?.trim();
      if (restoredPlayerId) {
        localAuthoritativePlayerIdRef.current = restoredPlayerId;
      }
      if (restoredTicketId) {
        queueTicketId = restoredTicketId;
      }
      battleIdRef.current = normalizedBattleId;
      setMatchPhase("playing");
      writeActiveBattleSession({ ...session, savedAt: Date.now() });

      while (!queueJoinCancelled && !finalizedRef.current) {
        const summary = await loadAuthoritativeResultSummaryWhenReady(normalizedBattleId);
        if (summary && !queueJoinCancelled && !finalizedRef.current) {
          settleAuthoritativeResult(summary, normalizedBattleId);
          return;
        }

        if (!runtimeHandleRef.current && !queueJoinCancelled && !finalizedRef.current) {
          const state = authoritativeBattleStateRef.current?.battleId === normalizedBattleId
            ? authoritativeBattleStateRef.current
            : await loadAuthoritativeBattleState(normalizedBattleId);
          if (state && !runtimeHandleRef.current && !queueJoinCancelled && !finalizedRef.current) {
            startBattleRuntime(
              session.snapshot,
              session.replayFrames,
              session.lastReplaySampleElapsed,
              undefined,
              state.battleId,
              state,
              true
            );
          }
        }

        await new Promise((resolve) => window.setTimeout(resolve, AUTHORITATIVE_RESULT_READY_RETRY_MS));
      }
    };

    const recoverCompletedSession = (session: ActiveBattleSession): void => {
      const isSharedAuthoritativeSession = session.sharedAuthoritativeRuntime === true;
      if (isSharedAuthoritativeSession) {
        void recoverSharedAuthoritativeCompletedSession(session);
        return;
      }

      const recoveredSnapshot = isSharedAuthoritativeSession
        ? session.snapshot
        : createExitedBattleSnapshot(session.snapshot);
      const { finalSnapshot, botOnlyClosure } = isSharedAuthoritativeSession
        ? { finalSnapshot: recoveredSnapshot, botOnlyClosure: null }
        : resolveBattleFinalizationSnapshot(recoveredSnapshot, false);
      const replayFrames = isSharedAuthoritativeSession
        ? [...session.replayFrames]
        : botOnlyClosure
          ? appendBotOnlyBattleClosureReplayFrames(session.replayFrames, botOnlyClosure)
          : buildResolvedReplayFrames(session.replayFrames, finalSnapshot);
      const finalized = finalizeLocalBattleWithFreshIdFallback({
        battleId: session.battleId,
        snapshot: finalSnapshot,
        finishedAt: Date.now(),
        thumbnailDataUrl: null,
        replayFrames,
        botOnlyClosure,
        allowBotOnlyClosure: !isSharedAuthoritativeSession,
        syncBackend: !isSharedAuthoritativeSession
      });

      if (!finalized) {
        writeCompletedActiveBattleSession({
          ...session,
          savedAt: Date.now(),
          snapshot: finalSnapshot,
          replayFrames
        });
        return;
      }

      clearActiveBattleSession(currentBattleSessionOwner);
      finalizedRef.current = true;
      setMatchPhase("settled");
      setCurrentResultSummary(finalized.returnSummary);
      setCurrentReplayId(finalized.replay.id);
    };

    const startBattleRuntime = (
      runtimeInitialSnapshot: GameSnapshot | null = null,
      restoredReplayFrames: ReplayFrame[] = [],
      restoredLastReplaySampleElapsed: number | null = null,
      initialParticipants?: InitialBattleParticipantsConfig,
      battleId?: string,
      initialAuthoritativeState: AuthoritativeBattleState | null = null,
      sharedAuthoritativeRuntime = false
    ): void => {
      if (!runtimeRootRef.current || !hudRootRef.current || runtimeHandleRef.current) {
        return;
      }

      const runtime = createBattleRuntime({
        mountNode: runtimeRootRef.current,
        hudRoot: hudRootRef.current,
        initialSnapshot: runtimeInitialSnapshot,
        initialParticipants,
        initialAuthoritativeState,
        localAuthoritativePlayerId: resolveLocalAuthoritativePlayerId(),
        sharedAuthoritativeRuntime
      });

      runtimeHandleRef.current = runtime;
      sharedAuthoritativeRuntimeRef.current = sharedAuthoritativeRuntime;
      authoritativeFirstFrameAppliedRef.current = !sharedAuthoritativeRuntime;
      runtime.setAuthoritativePreparedSkill(authoritativePreparedSkillRef.current);
      battleIdRef.current = initialAuthoritativeState?.battleId ?? battleId ?? resolveRuntimeBattleId();
      setActiveDrawer(null);
      replayFramesRef.current = [...restoredReplayFrames];
      lastReplaySampleFrameRef.current = null;
      lastReplaySampleElapsedRef.current = restoredLastReplaySampleElapsed;

      if (initialAuthoritativeState) {
        authoritativeBattleStateRef.current = initialAuthoritativeState;
        pruneAuthoritativeCommandHistoryFromState(initialAuthoritativeState);
        const applied = runtime.applyAuthoritativeState(
          initialAuthoritativeState,
          resolveLocalAuthoritativePlayerId(),
          authoritativeCommandHistoryRef.current.entries
        );
        if (applied) {
          authoritativeFirstFrameAppliedRef.current = true;
        }
      }

      const initialSnapshot =
        sharedAuthoritativeRuntime && !authoritativeFirstFrameAppliedRef.current ? null : readRuntimeSnapshot();
      if (initialSnapshot && replayFramesRef.current.length === 0) {
        pushReplayFrame(initialSnapshot, true);
      }
      if (initialSnapshot) {
        persistRuntime(false, initialSnapshot);
        setMatchPhase("playing");
      }

      const initialElapsedMs = Math.max(0, initialAuthoritativeState?.elapsedMs ?? initialSnapshot?.elapsedMs ?? 0);
      const battleDurationMs = Math.max(1, initialAuthoritativeState?.durationMs ?? BATTLE_MATCH_DURATION_MS);
      const battleDurationRemainingMs = Math.max(0, battleDurationMs - initialElapsedMs);
      battleDurationDeadlineRef.current = sharedAuthoritativeRuntime ? null : performance.now() + battleDurationRemainingMs;
      if (sharedAuthoritativeRuntime) {
        startAuthoritativeBattleBridge();
      } else {
        stopAuthoritativeBattleBridge();
      }

      snapshotTimerRef.current = window.setInterval(() => {
        const currentRuntime = runtimeHandleRef.current;
        if (!currentRuntime || finalizedRef.current) {
          return;
        }

        if (sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current) {
          return;
        }

        const snapshot = readRuntimeSnapshot();
        if (!snapshot) {
          return;
        }

        pushReplayFrame(snapshot);
        persistRuntime(false, snapshot);
        setMatchPhase("playing");
        if (isRuntimeBattleComplete(snapshot)) {
          finalizeRuntime(isAuthoritativeDurationExpired());
        } else if (isBattleDurationExpired()) {
          finalizeRuntime(true);
        }
      }, REPLAY_SAMPLE_INTERVAL_MS);

      battleEndTimerRef.current = window.setInterval(() => {
        finalizeRuntime(isAuthoritativeDurationExpired());
        if (!finalizedRef.current && isBattleDurationExpired()) {
          finalizeRuntime(true);
        }
      }, BATTLE_COMPLETION_CHECK_INTERVAL_MS);

      if (!sharedAuthoritativeRuntime) {
        battleDurationTimerRef.current = window.setTimeout(() => {
          finalizeRuntime(true);
        }, battleDurationRemainingMs);
      }

      window.setTimeout(() => {
        const snapshot = readRuntimeSnapshot();
        if (snapshot) {
          persistRuntime(true, snapshot);
          setMatchPhase("playing");
        }
      }, 0);
      finalizeRuntime(isBattleDurationExpired());
    };

    const persistRuntimeBeforePageExit = (): void => {
      if (discardSessionOnNextTeardownRef.current || newBattleResetPendingRef.current) {
        clearActiveBattleSession(currentBattleSessionOwner);
        return;
      }

      const shouldFinalizeOnExit = shouldFinalizeRuntimeOnExit();
      if (shouldFinalizeOnExit) {
        finalizeRuntime(isBattleDurationExpired(), false, true);
        if (finalizedRef.current) {
          return;
        }
      }

      persistRuntime(true);
    };

    window.addEventListener("pagehide", persistRuntimeBeforePageExit);
    window.addEventListener("beforeunload", persistRuntimeBeforePageExit);

    const removePageExitPersistence = (): void => {
      window.removeEventListener("pagehide", persistRuntimeBeforePageExit);
      window.removeEventListener("beforeunload", persistRuntimeBeforePageExit);
    };

    if (completedSession) {
      recoverCompletedSession(completedSession);
      completedSession = null;
      return () => {
        removePageExitPersistence();
        queueJoinCancelled = true;
        backendQueueJoinPendingRef.current = false;
        tearDownRuntime();
      };
    }

    setMatchPhase("matching");
    const isRestoringActiveSession = restoredActiveSession !== null;
    setMatchCountdownMs(isRestoringActiveSession ? 0 : MATCHMAKING_DURATION_MS);
    setQueueState(null);
    const matchWaitStartedAt = performance.now();
    const minimumMatchWaitDeadline = isRestoringActiveSession
      ? matchWaitStartedAt
      : matchWaitStartedAt + MATCHMAKING_DURATION_MS;
    countdownStartedAtRef.current = matchWaitStartedAt;
    matchWaitDeadlineRef.current = minimumMatchWaitDeadline;

    function resolveCurrentCountdownRemainingMs(): number {
      const sharedRemainingMs = resolveSharedQueueRemainingMs(queueStateRef.current);
      if (sharedRemainingMs !== null) {
        return sharedRemainingMs;
      }

      const fallbackStartedAt = countdownStartedAtRef.current ?? performance.now();
      const fallbackDeadline = fallbackStartedAt + MATCHMAKING_DURATION_MS;
      const deadline = matchWaitDeadlineRef.current ?? fallbackDeadline;
      return Math.max(0, deadline - performance.now());
    }

    function scheduleMatchStart(): void {
      if (battleStartLockedRef.current || finalizedRef.current) {
        return;
      }

      clearMatchStartTimer();
      if (backendQueueJoinPendingRef.current || !queueStateRef.current) {
        matchStartTimerRef.current = window.setTimeout(() => {
          void startScheduledBattle();
        }, MATCH_START_RECHECK_MS);
        return;
      }

      if (hasSharedBattleSession(queueStateRef.current)) {
        matchStartTimerRef.current = window.setTimeout(() => {
          void startScheduledBattle();
        }, MATCH_START_RECHECK_MS);
        return;
      }

      const remainingWaitMs = resolveCurrentCountdownRemainingMs();
      matchStartTimerRef.current = window.setTimeout(() => {
        void startScheduledBattle();
      }, remainingWaitMs + MATCH_START_RECHECK_MS);
    }

    function applyQueueState(nextQueueState: MatchmakingQueueState, syncDeadline: boolean): void {
      queueStateRef.current = nextQueueState;
      localAuthoritativePlayerIdRef.current = nextQueueState.playerId;
      setQueueState(nextQueueState);

      if (!syncDeadline) {
        return;
      }

      const sharedRemainingMs = resolveSharedQueueRemainingMs(nextQueueState);
      const minimumRemainingMs = Math.max(0, minimumMatchWaitDeadline - performance.now());
      const remainingWaitMs = sharedRemainingMs ?? minimumRemainingMs;
      matchWaitDeadlineRef.current = performance.now() + remainingWaitMs;
      setMatchCountdownMs(remainingWaitMs);
      scheduleMatchStart();
    }

    const loadLatestQueueStateForBattleStart = async (): Promise<MatchmakingQueueState | null> => {
      const ticketId = queueTicketId;
      if (!ticketId) {
        return queueStateRef.current;
      }

      const mustWaitForSharedBattleId = requiresAuthoritativeStartup(queueStateRef.current);
      const refreshDeadline = performance.now() + START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS;

      while (!queueJoinCancelled && performance.now() <= refreshDeadline) {
        const latest = await loadMatchmakingQueueStatus(ticketId);
        if (queueJoinCancelled) {
          return queueStateRef.current;
        }

        if (!latest) {
          if (!mustWaitForSharedBattleId) {
            return queueStateRef.current;
          }
        } else {
          applyQueueState(latest, false);
          if (!mustWaitForSharedBattleId || resolveBackendBattleId(latest)) {
            return latest;
          }
        }

        await new Promise((resolve) => window.setTimeout(resolve, 100));
      }

      return queueStateRef.current;
    };

    async function startScheduledBattle(): Promise<void> {
      if (battleStartLockedRef.current || finalizedRef.current) {
        return;
      }

      if (restoredActiveSession?.sharedAuthoritativeRuntime === true) {
        battleStartLockedRef.current = true;
        clearMatchStartTimer();
        clearCountdownTimer();
        setMatchCountdownMs(0);

        const restoredBattleId = restoredActiveSession.battleId.trim();
        const restoredPlayerId = restoredActiveSession.localAuthoritativePlayerId?.trim();
        const restoredTicketId = restoredActiveSession.localAuthoritativeTicketId?.trim();
        if (restoredPlayerId) {
          localAuthoritativePlayerIdRef.current = restoredPlayerId;
        }
        if (restoredTicketId) {
          queueTicketId = restoredTicketId;
        }

        const initialAuthoritativeState = restoredBattleId
          ? await loadAuthoritativeBattleState(restoredBattleId)
          : null;
        if (queueJoinCancelled || finalizedRef.current) {
          return;
        }

        if (isAuthoritativeStateRecoverable(initialAuthoritativeState, restoredBattleId)) {
          clearQueuePollingTimer();
          clearRoomPresenceTimer();
          startBattleRuntime(
            restoredActiveSession.snapshot,
            restoredActiveSession.replayFrames,
            restoredActiveSession.lastReplaySampleElapsed,
            undefined,
            initialAuthoritativeState.battleId,
            initialAuthoritativeState,
            true
          );
          return;
        }

        clearActiveBattleSession(currentBattleSessionOwner);
        restoredActiveSession = null;
        activeSessionEpochRef.current = publishActiveBattleSessionEpoch(currentBattleSessionOwner);
        queueTicketId = null;
        battleStartLockedRef.current = false;
        clearMatchStartTimer();
        void joinBackendQueue();
        scheduleMatchStart();
        return;
      }

      if (backendQueueJoinPendingRef.current || !queueStateRef.current) {
        clearMatchStartTimer();
        matchStartTimerRef.current = window.setTimeout(() => {
          void startScheduledBattle();
        }, MATCH_START_RECHECK_MS);
        return;
      }

      const remainingWaitMs = resolveCurrentCountdownRemainingMs();
      if (remainingWaitMs > 0) {
        clearMatchStartTimer();
        matchStartTimerRef.current = window.setTimeout(() => {
          void startScheduledBattle();
        }, remainingWaitMs + MATCH_START_RECHECK_MS);
        return;
      }

      battleStartLockedRef.current = true;
      clearMatchStartTimer();
      clearCountdownTimer();
      setMatchCountdownMs(0);

      const latestQueueState = await loadLatestQueueStateForBattleStart();
      if (queueJoinCancelled || finalizedRef.current) {
        return;
      }

      const runtimeQueueState = latestQueueState ?? queueStateRef.current;
      const mustWaitForAuthoritativeBootstrap = requiresAuthoritativeStartup(runtimeQueueState);
      const sharedBattleId = mustWaitForAuthoritativeBootstrap
        ? resolveBackendBattleId(runtimeQueueState)
        : resolveAuthoritativeRuntimeBattleId(runtimeQueueState);

      if (
        restoredActiveSession &&
        sharedBattleId &&
        restoredActiveSession.battleId.trim() !== sharedBattleId.trim()
      ) {
        clearActiveBattleSessionProgress(currentBattleSessionOwner);
        restoredActiveSession = null;
      }

      if (restoredActiveSession) {
        if (
          isActiveBattleSessionCompatibleWithQueueState(restoredActiveSession, runtimeQueueState) &&
          !mustWaitForAuthoritativeBootstrap
        ) {
          clearQueuePollingTimer();
          clearRoomPresenceTimer();
          startBattleRuntime(
            restoredActiveSession.snapshot,
            restoredActiveSession.replayFrames,
            restoredActiveSession.lastReplaySampleElapsed,
            undefined,
            restoredActiveSession.battleId,
            null,
            false
          );
          return;
        }

        clearActiveBattleSessionProgress(currentBattleSessionOwner);
        restoredActiveSession = null;
      }

      if (mustWaitForAuthoritativeBootstrap && !sharedBattleId) {
        battleStartLockedRef.current = false;
        clearMatchStartTimer();
        matchStartTimerRef.current = window.setTimeout(() => {
          void startScheduledBattle();
        }, AUTHORITATIVE_BOOTSTRAP_RETRY_MS);
        return;
      }

      if (mustWaitForAuthoritativeBootstrap && sharedBattleId) {
        const initialAuthoritativeState = await loadAuthoritativeBattleState(sharedBattleId);
        if (
          !isAuthoritativeStateRecoverable(initialAuthoritativeState, sharedBattleId) ||
          queueJoinCancelled ||
          finalizedRef.current
        ) {
          battleStartLockedRef.current = false;
          clearMatchStartTimer();
          matchStartTimerRef.current = window.setTimeout(() => {
            void startScheduledBattle();
          }, AUTHORITATIVE_BOOTSTRAP_RETRY_MS);
          return;
        }

        clearQueuePollingTimer();
        clearRoomPresenceTimer();
        startBattleRuntime(
          null,
          [],
          null,
          buildInitialBattleParticipants(loadout.handle, runtimeQueueState),
          initialAuthoritativeState.battleId,
          initialAuthoritativeState,
          true
        );
        return;
      }

      clearQueuePollingTimer();
      clearRoomPresenceTimer();
      startBattleRuntime(
        null,
        [],
        null,
        buildInitialBattleParticipants(loadout.handle, runtimeQueueState),
        sharedBattleId ?? resolveRuntimeBattleId(runtimeQueueState)
      );
    }

    scheduleMatchStart();

    const tickCountdown = (): void => {
      setMatchCountdownMs(resolveCurrentCountdownRemainingMs());
    };

    countdownTimerRef.current = window.setInterval(tickCountdown, 100);
    tickCountdown();

    const pollQueueStatus = async (ticketId: string): Promise<void> => {
      if (queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      const status = await loadMatchmakingQueueStatus(ticketId);
      if (!status || queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      applyQueueState(status, true);
      if (hasSharedBattleSession(status)) {
        clearQueuePollingTimer();
      }
    };

    const startQueuePolling = (ticketId: string): void => {
      if (queuePollingTimerRef.current !== null) {
        return;
      }

      queuePollingTimerRef.current = window.setInterval(() => {
        void pollQueueStatus(ticketId);
      }, 1000);
    };

    const scheduleQueueJoinRetry = (): void => {
      if (
        queuePollingTimerRef.current !== null ||
        queueJoinCancelled ||
        battleStartLockedRef.current ||
        queueTicketId ||
        backendQueueJoinPendingRef.current
      ) {
        return;
      }

      queuePollingTimerRef.current = window.setTimeout(() => {
        queuePollingTimerRef.current = null;
        if (!queueJoinCancelled && !battleStartLockedRef.current && !queueTicketId) {
          void joinBackendQueue();
        }
      }, 1000);
    };

    const refreshRoomPresence = async (): Promise<void> => {
      if (queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      const currentState = queueStateRef.current;
      if (!currentState) {
        return;
      }

      const presenceState = await refreshMatchmakingRoomPresence(currentState, loadout.handle);
      if (!presenceState || queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      applyQueueState(presenceState, true);
      if (hasSharedBattleSession(presenceState)) {
        clearQueuePollingTimer();
      }
    };

    const startRoomPresencePolling = (): void => {
      if (roomPresenceTimerRef.current !== null) {
        return;
      }

      void refreshRoomPresence();
      roomPresenceTimerRef.current = window.setInterval(() => {
        void refreshRoomPresence();
      }, 1000);
    };

    const joinBackendQueue = async (): Promise<void> => {
      if (
        queueJoinCancelled ||
        battleStartLockedRef.current ||
        queueTicketId ||
        backendQueueJoinPendingRef.current
      ) {
        return;
      }

      backendQueueJoinPendingRef.current = true;
      const joined = await joinMatchmakingQueue({
        handle: loadout.handle,
        sessionToken: currentBattleSessionOwner.sessionToken,
        queueRequestId,
        rating: loadout.rating,
        skin: loadout.skinId
      });
      backendQueueJoinPendingRef.current = false;
      if (!joined) {
        queueStateRef.current = null;
        localAuthoritativePlayerIdRef.current = null;
        setQueueState(null);
        scheduleQueueJoinRetry();
        return;
      }

      if (queueJoinCancelled || battleStartLockedRef.current) {
        leaveMatchmakingQueue(joined.ticketId);
        return;
      }

      queueTicketId = joined.ticketId;
      applyQueueState(joined, true);
      startQueuePolling(joined.ticketId);
      startRoomPresencePolling();
    };

    if (!isRestoringActiveSession) {
      void joinBackendQueue();
    }

    return () => {
      removePageExitPersistence();
      queueJoinCancelled = true;
      backendQueueJoinPendingRef.current = false;
      clearCountdownTimer();
      clearMatchStartTimer();
      clearQueuePollingTimer();
      clearRoomPresenceTimer();
      if (queueTicketId && !battleStartLockedRef.current) {
        leaveMatchmakingQueue(queueTicketId);
      }
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
  }, [currentBattleSessionOwner.handle, currentBattleSessionOwner.sessionToken, isBattleEntryBlocked, loadout.handle, matchNonce, location.search]);

  const clearTransientNotice = (): void => {
    if (transientNoticeTimerRef.current !== null) {
      window.clearTimeout(transientNoticeTimerRef.current);
      transientNoticeTimerRef.current = null;
    }
    transientNoticeLastShownRef.current = null;
    setTransientNotice(null);
  };

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
    ...pageData,
    openDrawer: setActiveDrawer,
    closeDrawer: () => setActiveDrawer(null),
    startNewMatch: () => {
      if (isVisitorBattleIdentity(currentBattleSessionOwner)) {
        setEntryBlockNotice(VISITOR_BATTLE_BLOCKED_MESSAGE);
        return;
      }

      discardSessionOnNextTeardownRef.current = true;
      newBattleResetPendingRef.current = true;
      countdownStartedAtRef.current = null;
      matchWaitDeadlineRef.current = null;
      battleIdRef.current = null;
      authoritativePreparedSkillRef.current = null;
      runtimeHandleRef.current?.setAuthoritativePreparedSkill(null);
      activeSessionEpochRef.current = publishActiveBattleSessionEpoch(currentBattleSessionOwner);
      clearActiveBattleSession(currentBattleSessionOwner);
      setMatchNonce((value) => value + 1);
    }
  };
}

function isVisitorBattleIdentity(owner: ActiveBattleSessionOwner): boolean {
  return !owner.sessionToken?.trim() || isBattleVisitorHandle(owner.handle);
}

interface AuthoritativePreparedInputResolution {
  input: AuthoritativeBattleInputSnapshot;
  preparedSkill: PreparedSkill;
  confirmedTarget: Vec2 | null;
}

type TargetedPreparedSkill = Exclude<PreparedSkill, null>;

interface PreparedSkillTransition {
  preparedSkill: PreparedSkill;
  castSkill: TargetedPreparedSkill | null;
  toggledPreparedSkill: boolean;
}

function resolveAuthoritativePreparedInput(
  runtimeCommand: PlayerCommand | null,
  fallback: AuthoritativeBattleInputSnapshot,
  preparedSkill: PreparedSkill
): AuthoritativePreparedInputResolution {
  const input = runtimeCommand
    ? toAuthoritativeInputSnapshot(runtimeCommand, fallback)
    : toFallbackAuthoritativeInputSnapshot(fallback);
  const transition = runtimeCommand
    ? resolvePreparedSkillTransition(preparedSkill, runtimeCommand)
    : {
        preparedSkill,
        castSkill: null,
        toggledPreparedSkill: false
      } satisfies PreparedSkillTransition;
  const confirmedTarget =
    runtimeCommand && transition.castSkill !== null ? cloneVec2(runtimeCommand.pointerWorld) : null;
  const castBlink = transition.castSkill === "Blink";
  const castFreeze = transition.castSkill === "Freeze";
  const suppressPrimaryHeld =
    input.castDash || castBlink || castFreeze || transition.preparedSkill !== null || transition.toggledPreparedSkill;

  return {
    input: {
      ...input,
      primaryHeld: suppressPrimaryHeld ? false : input.primaryHeld,
      castBlink,
      castFreeze,
      pointerWorld: confirmedTarget ?? input.pointerWorld
    },
    preparedSkill: transition.preparedSkill,
    confirmedTarget
  };
}

function resolvePreparedSkillTransition(
  preparedSkill: PreparedSkill,
  runtimeCommand: PlayerCommand
): PreparedSkillTransition {
  let nextPreparedSkill = preparedSkill;
  const toggledPreparedSkill = runtimeCommand.toggleBlink || runtimeCommand.toggleFreeze;

  if (runtimeCommand.toggleBlink) {
    nextPreparedSkill = nextPreparedSkill === "Blink" ? null : "Blink";
  }
  if (runtimeCommand.toggleFreeze) {
    nextPreparedSkill = nextPreparedSkill === "Freeze" ? null : "Freeze";
  }

  const confirmedSkill = runtimeCommand.primaryJustPressed
    ? resolveConfirmedPreparedSkill(runtimeCommand, nextPreparedSkill)
    : null;
  if (confirmedSkill !== null) {
    return {
      preparedSkill: null,
      castSkill: confirmedSkill,
      toggledPreparedSkill
    };
  }

  return {
    preparedSkill: nextPreparedSkill,
    castSkill: null,
    toggledPreparedSkill
  };
}

function resolveConfirmedPreparedSkill(
  runtimeCommand: PlayerCommand,
  preparedSkill: PreparedSkill
): TargetedPreparedSkill | null {
  if (runtimeCommand.toggleFreeze) {
    return "Freeze";
  }
  if (runtimeCommand.toggleBlink) {
    return "Blink";
  }

  return preparedSkill;
}

function toAuthoritativeInputSnapshot(
  command: PlayerCommand,
  fallback: AuthoritativeBattleInputSnapshot
): AuthoritativeBattleInputSnapshot {
  const hasCommandMovement = Math.hypot(command.movement.x, command.movement.y) > 0.0001;
  const hasFallbackMovement = Math.hypot(fallback.movement.x, fallback.movement.y) > 0.0001;
  const movement =
    hasCommandMovement || !hasFallbackMovement
      ? { x: command.movement.x, y: command.movement.y }
      : { x: fallback.movement.x, y: fallback.movement.y };

  return {
    movement,
    aim: { x: command.aim.x, y: command.aim.y },
    pointerWorld: { x: command.pointerWorld.x, y: command.pointerWorld.y },
    primaryHeld: command.primaryHeld || fallback.primaryHeld,
    sprint: command.sprint || fallback.sprint,
    reloadPressed: command.reloadPressed || fallback.reloadPressed,
    castDash: command.castDash || fallback.castDash,
    castBlink: false,
    castFreeze: false,
    switchWeaponDirection:
      command.switchWeaponDirection !== 0 ? command.switchWeaponDirection : fallback.switchWeaponDirection,
    switchWeaponIndex: command.switchWeaponIndex ?? fallback.switchWeaponIndex
  };
}

function toFallbackAuthoritativeInputSnapshot(
  fallback: AuthoritativeBattleInputSnapshot
): AuthoritativeBattleInputSnapshot {
  return {
    ...fallback,
    castBlink: false,
    castFreeze: false,
    switchWeaponIndex: fallback.switchWeaponIndex
  };
}

function cloneVec2(value: Vec2): Vec2 {
  return { x: value.x, y: value.y };
}

function createQueueRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `queue-${crypto.randomUUID()}`;
  }

  return `queue-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function resolveAuthoritativePlayerPosition(
  state: AuthoritativeBattleState | null,
  playerId: string
): Vec2 | null {
  const player = state?.players.find((entry) => entry.playerId === playerId);
  return player ? { x: player.position.x, y: player.position.y } : null;
}
