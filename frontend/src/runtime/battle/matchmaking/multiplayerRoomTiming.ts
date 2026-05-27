import type { MatchmakingQueueState } from "./matchmakingQueueTypes";

/** 中文名：解析共享房间nowms（resolveSharedRoomNowMs）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：解析共享队列remainingms（resolveSharedQueueRemainingMs）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：判断是否有共享战斗会话（hasSharedBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function hasSharedBattleSession(queueState: MatchmakingQueueState | null): boolean {
  const battleId = queueState?.battleSession?.battleId?.trim();
  return Boolean(battleId);
}
