import type { MatchmakingQueueState } from "./matchmakingQueueTypes";

export function resolveSharedRoomNowMs(
  queueState: MatchmakingQueueState | null,
  clientNowMs: number = Date.now()
): number | null {
  const referenceServerTime = queueState?.battleSession?.serverTime ?? queueState?.serverTime;
  const referenceSyncedAt = queueState?.syncedAt ?? null;
  if (
    referenceServerTime === undefined ||
    referenceServerTime === null ||
    referenceSyncedAt === null ||
    !Number.isFinite(referenceServerTime) ||
    !Number.isFinite(referenceSyncedAt)
  ) {
    return null;
  }

  return referenceServerTime + Math.max(0, clientNowMs - referenceSyncedAt);
}

export function resolveSharedQueueRemainingMs(
  queueState: MatchmakingQueueState | null,
  clientNowMs: number = Date.now()
): number | null {
  if (queueState?.source !== "backend") {
    return null;
  }

  if (hasSharedBattleSession(queueState)) {
    return 0;
  }

  const sharedNowMs = resolveSharedRoomNowMs(queueState, clientNowMs);
  if (sharedNowMs === null) {
    return null;
  }

  return Math.max(0, queueState.startsAt - sharedNowMs);
}

export function hasSharedBattleSession(queueState: MatchmakingQueueState | null): boolean {
  const battleId = queueState?.battleSession?.battleId?.trim();
  return Boolean(battleId);
}
