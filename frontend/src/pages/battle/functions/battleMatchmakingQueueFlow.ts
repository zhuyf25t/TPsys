import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { hasSharedBattleSession } from "../../../runtime/battle/matchmaking/multiplayerRoomTiming";
import type { BattlePlayModeId } from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";

export interface BattleQueueJoinInput {
  readonly handle: string;
  readonly sessionToken: string | null;
  readonly modeId: BattlePlayModeId;
  readonly queueRequestId: string;
  readonly rating?: number;
  readonly skin?: string;
}

interface BuildBattleQueueJoinInput {
  readonly handle: string;
  readonly sessionToken: string | null;
  readonly selectedBattleModeId: BattlePlayModeId;
  readonly queueRequestId: string;
  readonly rating?: number;
  readonly skinId?: string;
}

interface QueueJoinRetryInput {
  readonly queuePollingTimerActive: boolean;
  readonly queueJoinCancelled: boolean;
  readonly battleStartLocked: boolean;
  readonly queueTicketId: string | null;
  readonly backendQueueJoinPending: boolean;
}

interface QueueStatusUsableInput {
  readonly queueJoinCancelled: boolean;
  readonly battleStartLocked: boolean;
}

export function buildBattleQueueJoinInput({
  handle,
  sessionToken,
  selectedBattleModeId,
  queueRequestId,
  rating,
  skinId
}: BuildBattleQueueJoinInput): BattleQueueJoinInput {
  return {
    handle,
    sessionToken,
    modeId: selectedBattleModeId,
    queueRequestId,
    ...(typeof rating === "number" ? { rating } : {}),
    ...(skinId ? { skin: skinId } : {})
  };
}

export function shouldScheduleQueueJoinRetry({
  queuePollingTimerActive,
  queueJoinCancelled,
  battleStartLocked,
  queueTicketId,
  backendQueueJoinPending
}: QueueJoinRetryInput): boolean {
  return !(
    queuePollingTimerActive ||
    queueJoinCancelled ||
    battleStartLocked ||
    queueTicketId ||
    backendQueueJoinPending
  );
}

export function isQueueStatusResultUsable(
  status: MatchmakingQueueState | null,
  { queueJoinCancelled, battleStartLocked }: QueueStatusUsableInput
): status is MatchmakingQueueState {
  return Boolean(status) && !queueJoinCancelled && !battleStartLocked;
}

export function shouldStopQueuePollingForState(state: MatchmakingQueueState): boolean {
  return hasSharedBattleSession(state);
}
