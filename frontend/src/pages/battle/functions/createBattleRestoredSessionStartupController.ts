import {
  loadAuthoritativeBattleState,
  type AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import {
  clearActiveBattleSession,
  clearActiveBattleSessionProgress,
  publishActiveBattleSessionEpoch
} from "../stores/activeBattleSessionStore";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";
import { isActiveBattleSessionCompatibleWithQueueState } from "./battlePageRuntimeHelpers";
import { isAuthoritativeStateRecoverable } from "./authoritativeBattleStatePredicates";
import {
  isSharedAuthoritativeActiveSession,
  resolveAuthoritativeSessionRestoreIdentity
} from "./authoritativeSessionRestore";

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

interface BattleRestoredSessionQueueRuntime {
  ticketId: string | null;
  cancelled: boolean;
}

type RestoredSessionStartupResult = { readonly kind: "started" | "not_started" };

interface TryStartCompatibleRestoredSessionInput {
  readonly runtimeQueueState: MatchmakingQueueState | null;
  readonly sharedBattleId: string | null;
  readonly mustWaitForAuthoritativeBootstrap: boolean;
}

interface BattleRestoredSessionStartupControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly queueRuntime: BattleRestoredSessionQueueRuntime;
  readonly localAuthoritativePlayerIdRef: MutableRef<string | null>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly readRestoredActiveSession: () => ActiveBattleSession | null;
  readonly setRestoredActiveSession: (session: ActiveBattleSession | null) => void;
  readonly startBattleRuntime: StartBattleRuntime;
  readonly joinBackendQueue: () => Promise<void>;
  readonly clearMatchStartTimer: () => void;
  readonly clearCountdownTimer: () => void;
  readonly clearQueuePollingTimer: () => void;
  readonly clearRoomPresenceTimer: () => void;
  readonly setMatchCountdownMs: (value: number) => void;
  readonly scheduleMatchStart: () => void;
}

export function createBattleRestoredSessionStartupController({
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
}: BattleRestoredSessionStartupControllerOptions) {
  const tryStartSharedAuthoritativeRestoredSession = async (): Promise<RestoredSessionStartupResult> => {
    const restoredActiveSession = readRestoredActiveSession();
    if (!restoredActiveSession || !isSharedAuthoritativeActiveSession(restoredActiveSession)) {
      return { kind: "not_started" };
    }

    await startSharedAuthoritativeRestoredSession(restoredActiveSession);
    return { kind: "started" };
  };

  async function startSharedAuthoritativeRestoredSession(
    restoredActiveSession: ActiveBattleSession
  ): Promise<void> {
    battleStartLockedRef.current = true;
    clearMatchStartTimer();
    clearCountdownTimer();
    setMatchCountdownMs(0);

    const restoreIdentity = resolveAuthoritativeSessionRestoreIdentity(restoredActiveSession);
    if (restoreIdentity.localAuthoritativePlayerId) {
      localAuthoritativePlayerIdRef.current = restoreIdentity.localAuthoritativePlayerId;
    }
    if (restoreIdentity.localAuthoritativeTicketId) {
      queueRuntime.ticketId = restoreIdentity.localAuthoritativeTicketId;
    }

    const initialAuthoritativeState = restoreIdentity.battleId
      ? await loadAuthoritativeBattleState(restoreIdentity.battleId)
      : null;
    if (queueRuntime.cancelled || finalizedRef.current) {
      return;
    }

    if (isAuthoritativeStateRecoverable(initialAuthoritativeState, restoreIdentity.battleId)) {
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

    clearActiveBattleSession(owner);
    setRestoredActiveSession(null);
    activeSessionEpochRef.current = publishActiveBattleSessionEpoch(owner);
    queueRuntime.ticketId = null;
    battleStartLockedRef.current = false;
    clearMatchStartTimer();
    void joinBackendQueue();
    scheduleMatchStart();
  }

  const tryStartCompatibleRestoredSession = ({
    runtimeQueueState,
    sharedBattleId,
    mustWaitForAuthoritativeBootstrap
  }: TryStartCompatibleRestoredSessionInput): RestoredSessionStartupResult => {
    const restoredActiveSession = readRestoredActiveSession();
    if (
      restoredActiveSession &&
      sharedBattleId &&
      restoredActiveSession.battleId.trim() !== sharedBattleId.trim()
    ) {
      clearActiveBattleSessionProgress(owner);
      setRestoredActiveSession(null);
    }

    const currentRestoredSession = readRestoredActiveSession();
    if (!currentRestoredSession) {
      return { kind: "not_started" };
    }

    if (
      isActiveBattleSessionCompatibleWithQueueState(currentRestoredSession, runtimeQueueState) &&
      !mustWaitForAuthoritativeBootstrap
    ) {
      clearQueuePollingTimer();
      clearRoomPresenceTimer();
      startBattleRuntime(
        currentRestoredSession.snapshot,
        currentRestoredSession.replayFrames,
        currentRestoredSession.lastReplaySampleElapsed,
        undefined,
        currentRestoredSession.battleId,
        null,
        false
      );
      return { kind: "started" };
    }

    clearActiveBattleSessionProgress(owner);
    setRestoredActiveSession(null);
    return { kind: "not_started" };
  };

  return {
    tryStartCompatibleRestoredSession,
    tryStartSharedAuthoritativeRestoredSession
  };
}
