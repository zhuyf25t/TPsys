import type { MatchmakingQueueState } from "../../../../../../../runtime/battle/matchmaking/matchmakingQueueTypes";

export function formatQueueLabel(queueState: MatchmakingQueueState | null): string {
  if (!queueState) {
    return "等待匹配";
  }

  if (queueState.phase === "finished") {
    return "战斗已结束";
  }

  return queueState.phase === "active" ? "房间已就绪" : "等待成员";
}

export function formatPhaseLabel(queueState: MatchmakingQueueState | null): string {
  if (!queueState) {
    return "等待同步";
  }

  if (queueState.phase === "finished") {
    return "结算中";
  }

  return queueState.phase === "active" ? "即将开战" : "等待成员";
}

export function shortenRoomId(roomId: string): string {
  const normalized = roomId.trim();
  if (normalized.length <= 16) {
    return normalized;
  }

  return `${normalized.slice(0, 8)}-${normalized.slice(-6)}`;
}
