import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import {
  inferBattleModeIdFromMapId,
  type BattlePlayModeId
} from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import type { ActiveBattleSession, ActiveBattleSessionOwner, MatchPhase } from "../objects/BattlePageState";
import { MATCHMAKING_DURATION_MS } from "../objects/BattlePageTiming";
import {
  buildInitialBattleParticipants,
  MATCH_START_RECHECK_MS,
  START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS
} from "./battlePageRuntimeHelpers";
import {
  resolveMatchCountdownRemainingMs,
  resolveMinimumMatchWaitDeadline,
  resolveSyncedMatchWaitRemainingMs
} from "./battleMatchmakingSchedule";
import {
  createBattleMatchmakingQueueController,
  type BattleMatchmakingQueueRuntimeState
} from "./createBattleMatchmakingQueueController";
import { createBattleStartupSchedulerController } from "./createBattleStartupSchedulerController";
import { createQueueRequestId } from "./createQueueRequestId";
import { AUTHORITATIVE_BOOTSTRAP_RETRY_MS } from "../objects/BattlePageRuntimeConfig";
import type { StartBattleRuntime } from "./createBattleRuntimeLaunchController";

export type { BattleMatchmakingQueueRuntimeState } from "./createBattleMatchmakingQueueController";

interface MutableRef<T> {
  current: T;
}

interface BattleMatchmakingStartupLoadout {
  readonly handle: string;
  readonly rating?: number;
  readonly skinId?: string;
}

interface BattleMatchmakingStartupControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly loadout: BattleMatchmakingStartupLoadout;
  readonly selectedBattleModeId: BattlePlayModeId;
  readonly queueRuntime: BattleMatchmakingQueueRuntimeState;
  readonly queueStateRef: MutableRef<MatchmakingQueueState | null>;
  readonly localAuthoritativePlayerIdRef: MutableRef<string | null>;
  readonly backendQueueJoinPendingRef: MutableRef<boolean>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly countdownStartedAtRef: MutableRef<number | null>;
  readonly matchWaitDeadlineRef: MutableRef<number | null>;
  readonly countdownTimerRef: MutableRef<number | null>;
  readonly matchStartTimerRef: MutableRef<number | null>;
  readonly queuePollingTimerRef: MutableRef<number | null>;
  readonly roomPresenceTimerRef: MutableRef<number | null>;
  readonly readRestoredActiveSession: () => ActiveBattleSession | null;
  readonly setRestoredActiveSession: (session: ActiveBattleSession | null) => void;
  readonly resolveAuthoritativeRuntimeBattleId: (queueState?: MatchmakingQueueState | null) => string | null;
  readonly resolveRuntimeBattleId: (queueState?: MatchmakingQueueState | null) => string;
  readonly startBattleRuntime: StartBattleRuntime;
  readonly clearMatchStartTimer: () => void;
  readonly clearCountdownTimer: () => void;
  readonly clearQueuePollingTimer: () => void;
  readonly clearRoomPresenceTimer: () => void;
  readonly setMatchPhase: (phase: MatchPhase) => void;
  readonly setMatchCountdownMs: (value: number) => void;
  readonly setQueueState: (state: MatchmakingQueueState | null) => void;
  readonly setSelectedBattleModeId: (modeId: BattlePlayModeId) => void;
  readonly normalizeWaitingRoomQueueState?: (state: MatchmakingQueueState) => MatchmakingQueueState;
}

export function createBattleMatchmakingStartupController({
  owner,
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
  readRestoredActiveSession,
  setRestoredActiveSession,
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
  setSelectedBattleModeId,
  normalizeWaitingRoomQueueState
}: BattleMatchmakingStartupControllerOptions) {
  setMatchPhase("matching");
  const isRestoringActiveSession = readRestoredActiveSession() !== null;
  setMatchCountdownMs(isRestoringActiveSession ? 0 : MATCHMAKING_DURATION_MS);
  setQueueState(null);
  const matchWaitStartedAt = performance.now();
  const minimumMatchWaitDeadline = resolveMinimumMatchWaitDeadline({
    matchWaitStartedAt,
    isRestoringActiveSession,
    matchmakingDurationMs: MATCHMAKING_DURATION_MS
  });
  countdownStartedAtRef.current = matchWaitStartedAt;
  matchWaitDeadlineRef.current = minimumMatchWaitDeadline;

  const resolveCurrentCountdownRemainingMs = (): number =>
    resolveMatchCountdownRemainingMs({
      queueState: queueStateRef.current,
      countdownStartedAt: countdownStartedAtRef.current,
      matchWaitDeadline: matchWaitDeadlineRef.current,
      now: performance.now(),
      matchmakingDurationMs: MATCHMAKING_DURATION_MS
    });

  let startupScheduler: ReturnType<typeof createBattleStartupSchedulerController>;
  const applyQueueState = (nextQueueState: MatchmakingQueueState, syncDeadline: boolean): void => {
    const resolvedQueueState = normalizeWaitingRoomQueueState?.(nextQueueState) ?? nextQueueState;
    queueStateRef.current = resolvedQueueState;
    localAuthoritativePlayerIdRef.current = resolvedQueueState.playerId;
    setSelectedBattleModeId(inferBattleModeIdFromMapId(resolvedQueueState.mapId));
    setQueueState(resolvedQueueState);

    if (!syncDeadline) {
      return;
    }

    const now = performance.now();
    const remainingWaitMs = resolveSyncedMatchWaitRemainingMs({
      queueState: resolvedQueueState,
      minimumMatchWaitDeadline,
      now
    });
    matchWaitDeadlineRef.current = now + remainingWaitMs;
    setMatchCountdownMs(remainingWaitMs);
    startupScheduler.scheduleMatchStart();
  };

  const {
    joinBackendQueue,
    leaveJoinedQueueIfIdle
  } = createBattleMatchmakingQueueController({
    queueRuntime,
    queueStateRef,
    localAuthoritativePlayerIdRef,
    backendQueueJoinPendingRef,
    battleStartLockedRef,
    queuePollingTimerRef,
    roomPresenceTimerRef,
    owner,
    loadout,
    selectedBattleModeId,
    queueRequestId: createQueueRequestId(),
    clearQueuePollingTimer,
    applyQueueState,
    clearQueueState: () => {
      setQueueState(null);
    }
  });

  startupScheduler = createBattleStartupSchedulerController({
    owner,
    loadout,
    queueRuntime,
    queueStateRef,
    localAuthoritativePlayerIdRef,
    backendQueueJoinPendingRef,
    battleStartLockedRef,
    finalizedRef,
    activeSessionEpochRef,
    matchStartTimerRef,
    readRestoredActiveSession,
    setRestoredActiveSession,
    resolveCurrentCountdownRemainingMs,
    resolveAuthoritativeRuntimeBattleId,
    resolveRuntimeBattleId,
    buildInitialBattleParticipants,
    applyQueueState,
    startBattleRuntime,
    joinBackendQueue,
    clearMatchStartTimer,
    clearCountdownTimer,
    clearQueuePollingTimer,
    clearRoomPresenceTimer,
    setMatchCountdownMs,
    matchStartRecheckMs: MATCH_START_RECHECK_MS,
    authoritativeBootstrapRetryMs: AUTHORITATIVE_BOOTSTRAP_RETRY_MS,
    startBattleQueueRefreshTimeoutMs: START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS
  });

  startupScheduler.scheduleMatchStart();

  const tickCountdown = (): void => {
    setMatchCountdownMs(resolveCurrentCountdownRemainingMs());
  };

  countdownTimerRef.current = window.setInterval(tickCountdown, 100);
  tickCountdown();

  if (!isRestoringActiveSession) {
    void joinBackendQueue();
  }

  return {
    leaveJoinedQueueIfIdle
  };
}
