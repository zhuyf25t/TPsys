import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import {
  joinMatchmakingQueue,
  leaveMatchmakingQueue,
  loadMatchmakingQueueStatus,
  refreshMatchmakingRoomPresence
} from "../../../runtime/battle/matchmaking/matchmakingQueueGateway";
import type { BattlePlayModeId } from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import type { ActiveBattleSessionOwner } from "../objects/BattlePageState";
import {
  buildBattleQueueJoinInput,
  isQueueStatusResultUsable,
  shouldScheduleQueueJoinRetry,
  shouldStopQueuePollingForState
} from "./battleMatchmakingQueueFlow";

interface MutableRef<T> {
  current: T;
}

export interface BattleMatchmakingQueueRuntimeState {
  ticketId: string | null;
  cancelled: boolean;
}

interface BattleMatchmakingQueueLoadout {
  readonly handle: string;
  readonly rating?: number;
  readonly skinId?: string;
}

interface BattleMatchmakingQueueControllerOptions {
  readonly queueRuntime: BattleMatchmakingQueueRuntimeState;
  readonly queueStateRef: MutableRef<MatchmakingQueueState | null>;
  readonly localAuthoritativePlayerIdRef: MutableRef<string | null>;
  readonly backendQueueJoinPendingRef: MutableRef<boolean>;
  readonly battleStartLockedRef: MutableRef<boolean>;
  readonly queuePollingTimerRef: MutableRef<number | null>;
  readonly roomPresenceTimerRef: MutableRef<number | null>;
  readonly owner: ActiveBattleSessionOwner;
  readonly loadout: BattleMatchmakingQueueLoadout;
  readonly selectedBattleModeId: BattlePlayModeId;
  readonly queueRequestId: string;
  readonly clearQueuePollingTimer: () => void;
  readonly applyQueueState: (nextQueueState: MatchmakingQueueState, syncDeadline: boolean) => void;
  readonly clearQueueState: () => void;
}

export function createBattleMatchmakingQueueController({
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
  queueRequestId,
  clearQueuePollingTimer,
  applyQueueState,
  clearQueueState
}: BattleMatchmakingQueueControllerOptions) {
  const pollQueueStatus = async (ticketId: string): Promise<void> => {
    if (queueRuntime.cancelled || battleStartLockedRef.current) {
      return;
    }

    const status = await loadMatchmakingQueueStatus(ticketId);
    if (!isQueueStatusResultUsable(status, {
      queueJoinCancelled: queueRuntime.cancelled,
      battleStartLocked: battleStartLockedRef.current
    })) {
      return;
    }

    applyQueueState(status, true);
    if (shouldStopQueuePollingForState(status)) {
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

  const refreshRoomPresence = async (): Promise<void> => {
    if (queueRuntime.cancelled || battleStartLockedRef.current) {
      return;
    }

    const currentState = queueStateRef.current;
    if (!currentState) {
      return;
    }

    const presenceState = await refreshMatchmakingRoomPresence(currentState, loadout.handle);
    if (!isQueueStatusResultUsable(presenceState, {
      queueJoinCancelled: queueRuntime.cancelled,
      battleStartLocked: battleStartLockedRef.current
    })) {
      return;
    }

    applyQueueState(presenceState, true);
    if (shouldStopQueuePollingForState(presenceState)) {
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

  const scheduleQueueJoinRetry = (): void => {
    if (!shouldScheduleQueueJoinRetry({
      queuePollingTimerActive: queuePollingTimerRef.current !== null,
      queueJoinCancelled: queueRuntime.cancelled,
      battleStartLocked: battleStartLockedRef.current,
      queueTicketId: queueRuntime.ticketId,
      backendQueueJoinPending: backendQueueJoinPendingRef.current
    })) {
      return;
    }

    queuePollingTimerRef.current = window.setTimeout(() => {
      queuePollingTimerRef.current = null;
      if (!queueRuntime.cancelled && !battleStartLockedRef.current && !queueRuntime.ticketId) {
        void joinBackendQueue();
      }
    }, 1000);
  };

  const joinBackendQueue = async (): Promise<void> => {
    if (
      queueRuntime.cancelled ||
      battleStartLockedRef.current ||
      queueRuntime.ticketId ||
      backendQueueJoinPendingRef.current
    ) {
      return;
    }

    backendQueueJoinPendingRef.current = true;
    const joined = await joinMatchmakingQueue(buildBattleQueueJoinInput({
      handle: loadout.handle,
      sessionToken: owner.sessionToken,
      selectedBattleModeId,
      queueRequestId,
      rating: loadout.rating,
      skinId: loadout.skinId
    }));
    backendQueueJoinPendingRef.current = false;
    if (!joined) {
      queueStateRef.current = null;
      localAuthoritativePlayerIdRef.current = null;
      clearQueueState();
      scheduleQueueJoinRetry();
      return;
    }

    if (queueRuntime.cancelled || battleStartLockedRef.current) {
      leaveMatchmakingQueue(joined.ticketId);
      return;
    }

    queueRuntime.ticketId = joined.ticketId;
    applyQueueState(joined, true);
    startQueuePolling(joined.ticketId);
    startRoomPresencePolling();
  };

  const leaveJoinedQueueIfIdle = (): void => {
    if (queueRuntime.ticketId && !battleStartLockedRef.current) {
      leaveMatchmakingQueue(queueRuntime.ticketId);
    }
  };

  return {
    joinBackendQueue,
    leaveJoinedQueueIfIdle
  };
}
