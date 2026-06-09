import {
  loadAuthoritativeBattleState,
  type AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import { loadMatchmakingQueueStatus } from "../../../runtime/battle/matchmaking/matchmakingQueueGateway";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";
import { resolveBackendBattleId } from "./battlePageRuntimeHelpers";
import { isAuthoritativeStateRecoverable } from "./authoritativeBattleStatePredicates";
import { resolveMatchStartDelayMs } from "./battleMatchmakingSchedule";
import { createBattleRestoredSessionStartupController } from "./createBattleRestoredSessionStartupController";
import { requiresScheduledAuthoritativeStartup } from "./battleRuntimeIdentityResolvers";

interface MutableRef<T> {
  current: T;
}

type StartBattleRuntime = (
  runtimeInitialSnapshot?: GameSnapshot | null,
  restoredReplayFrames?: ReplayFrame[],
  restoredLastReplaySampleElapsed?: number | null,
  initialParticipants?: BattleInitialParticipantsConfig,
  battleId?: string,
  initialAuthoritativeState?: AuthoritativeBattleState | null,
  sharedAuthoritativeRuntime?: boolean
) => void;

interface BattleStartupSchedulerLoadout {
  readonly handle: string;
}

interface BattleStartupSchedulerQueueRuntime {
  ticketId: string | null;
  cancelled: boolean;
}

interface BattleStartupSchedulerControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly loadout: BattleStartupSchedulerLoadout;
  readonly queueRuntime: BattleStartupSchedulerQueueRuntime;
  readonly queueStateRef: MutableRef<MatchmakingQueueState | null>;
  readonly localAuthoritativePlayerIdRef: MutableRef<string | null>;
  readonly backendQueueJoinPendingRef: MutableRef<boolean>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly matchStartTimerRef: MutableRef<number | null>;
  readonly readRestoredActiveSession: () => ActiveBattleSession | null;
  readonly setRestoredActiveSession: (session: ActiveBattleSession | null) => void;
  readonly resolveCurrentCountdownRemainingMs: () => number;
  readonly resolveAuthoritativeRuntimeBattleId: (queueState?: MatchmakingQueueState | null) => string | null;
  readonly resolveRuntimeBattleId: (queueState?: MatchmakingQueueState | null) => string;
  readonly buildInitialBattleParticipants: (
    handle: string,
    queueState: MatchmakingQueueState | null
  ) => BattleInitialParticipantsConfig;
  readonly applyQueueState: (nextQueueState: MatchmakingQueueState, syncDeadline: boolean) => void;
  readonly startBattleRuntime: StartBattleRuntime;
  readonly joinBackendQueue: () => Promise<void>;
  readonly clearMatchStartTimer: () => void;
  readonly clearCountdownTimer: () => void;
  readonly clearQueuePollingTimer: () => void;
  readonly clearRoomPresenceTimer: () => void;
  readonly setMatchCountdownMs: (value: number) => void;
  readonly matchStartRecheckMs: number;
  readonly authoritativeBootstrapRetryMs: number;
  readonly startBattleQueueRefreshTimeoutMs: number;
}

export function createBattleStartupSchedulerController({
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
  matchStartRecheckMs,
  authoritativeBootstrapRetryMs,
  startBattleQueueRefreshTimeoutMs
}: BattleStartupSchedulerControllerOptions) {
  const scheduleMatchStart = (): void => {
    if (battleStartLockedRef.current || finalizedRef.current) {
      return;
    }

    clearMatchStartTimer();
    const delayMs = resolveMatchStartDelayMs({
      backendQueueJoinPending: backendQueueJoinPendingRef.current,
      queueState: queueStateRef.current,
      remainingWaitMs: resolveCurrentCountdownRemainingMs(),
      recheckMs: matchStartRecheckMs
    });
    matchStartTimerRef.current = window.setTimeout(() => {
      void startScheduledBattle();
    }, delayMs);
  };

  const {
    tryStartCompatibleRestoredSession,
    tryStartSharedAuthoritativeRestoredSession
  } = createBattleRestoredSessionStartupController({
    owner,
    queueRuntime,
    localAuthoritativePlayerIdRef,
    battleStartLockedRef,
    finalizedRef,
    activeSessionEpochRef,
    readRestoredActiveSession,
    setRestoredActiveSession,
    startBattleRuntime,
    joinBackendQueue,
    clearMatchStartTimer,
    clearCountdownTimer,
    clearQueuePollingTimer,
    clearRoomPresenceTimer,
    setMatchCountdownMs,
    scheduleMatchStart
  });

  const loadLatestQueueStateForBattleStart = async (): Promise<MatchmakingQueueState | null> => {
    const ticketId = queueRuntime.ticketId;
    if (!ticketId) {
      return queueStateRef.current;
    }

    const mustWaitForSharedBattleId = requiresScheduledAuthoritativeStartup({
      backendQueueJoinPending: backendQueueJoinPendingRef.current,
      queueTicketId: queueRuntime.ticketId,
      queueState: queueStateRef.current,
      previousQueueState: null
    });
    const refreshDeadline = performance.now() + startBattleQueueRefreshTimeoutMs;

    while (!queueRuntime.cancelled && performance.now() <= refreshDeadline) {
      const latest = await loadMatchmakingQueueStatus(ticketId);
      if (queueRuntime.cancelled) {
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

    const sharedRestoredSessionStartup = await tryStartSharedAuthoritativeRestoredSession();
    if (sharedRestoredSessionStartup.kind === "started") {
      return;
    }

    if (backendQueueJoinPendingRef.current || !queueStateRef.current) {
      clearMatchStartTimer();
      matchStartTimerRef.current = window.setTimeout(() => {
        void startScheduledBattle();
      }, resolveMatchStartDelayMs({
        backendQueueJoinPending: backendQueueJoinPendingRef.current,
        queueState: queueStateRef.current,
        remainingWaitMs: 0,
        recheckMs: matchStartRecheckMs
      }));
      return;
    }

    if (queueStateRef.current.startPaused) {
      clearMatchStartTimer();
      matchStartTimerRef.current = window.setTimeout(() => {
        void startScheduledBattle();
      }, matchStartRecheckMs);
      return;
    }

    const remainingWaitMs = resolveCurrentCountdownRemainingMs();
    if (remainingWaitMs > 0) {
      clearMatchStartTimer();
      matchStartTimerRef.current = window.setTimeout(() => {
        void startScheduledBattle();
      }, remainingWaitMs + matchStartRecheckMs);
      return;
    }

    battleStartLockedRef.current = true;
    clearMatchStartTimer();
    clearCountdownTimer();
    setMatchCountdownMs(0);

    const queueStateBeforeRefresh = queueStateRef.current;
    const latestQueueState = await loadLatestQueueStateForBattleStart();
    if (queueRuntime.cancelled || finalizedRef.current) {
      return;
    }

    const runtimeQueueState = latestQueueState ?? queueStateRef.current ?? queueStateBeforeRefresh;
    const mustWaitForAuthoritativeBootstrap = requiresScheduledAuthoritativeStartup({
      backendQueueJoinPending: backendQueueJoinPendingRef.current,
      queueTicketId: queueRuntime.ticketId,
      queueState: runtimeQueueState,
      previousQueueState: queueStateBeforeRefresh
    });
    const sharedBattleId = mustWaitForAuthoritativeBootstrap
      ? resolveBackendBattleId(runtimeQueueState)
      : resolveAuthoritativeRuntimeBattleId(runtimeQueueState);

    const compatibleRestoredSession = tryStartCompatibleRestoredSession({
      runtimeQueueState,
      sharedBattleId,
      mustWaitForAuthoritativeBootstrap
    });
    if (compatibleRestoredSession.kind === "started") {
      return;
    }

    if (mustWaitForAuthoritativeBootstrap && !sharedBattleId) {
      retryAuthoritativeBootstrap();
      return;
    }

    if (mustWaitForAuthoritativeBootstrap && sharedBattleId) {
      await startAuthoritativeRuntime(sharedBattleId, runtimeQueueState);
      return;
    }

    if (runtimeQueueState?.source !== "local") {
      retryAuthoritativeBootstrap();
      void joinBackendQueue();
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

  async function startAuthoritativeRuntime(
    sharedBattleId: string,
    runtimeQueueState: MatchmakingQueueState | null
  ): Promise<void> {
    const initialAuthoritativeState = await loadAuthoritativeBattleState(sharedBattleId);
    if (
      !isAuthoritativeStateRecoverable(initialAuthoritativeState, sharedBattleId) ||
      queueRuntime.cancelled ||
      finalizedRef.current
    ) {
      retryAuthoritativeBootstrap();
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
  }

  function retryAuthoritativeBootstrap(): void {
    battleStartLockedRef.current = false;
    clearMatchStartTimer();
    matchStartTimerRef.current = window.setTimeout(() => {
      void startScheduledBattle();
    }, authoritativeBootstrapRetryMs);
  }

  return {
    scheduleMatchStart,
    startScheduledBattle
  };
}
