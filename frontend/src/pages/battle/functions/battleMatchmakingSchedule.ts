import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import {
  hasSharedBattleSession,
  resolveSharedQueueRemainingMs
} from "../../../runtime/battle/matchmaking/multiplayerRoomTiming";

interface MatchWaitDeadlineInput {
  readonly matchWaitStartedAt: number;
  readonly isRestoringActiveSession: boolean;
  readonly matchmakingDurationMs: number;
}

interface MatchCountdownRemainingInput {
  readonly queueState: MatchmakingQueueState | null;
  readonly countdownStartedAt: number | null;
  readonly matchWaitDeadline: number | null;
  readonly now: number;
  readonly matchmakingDurationMs: number;
}

interface SyncedMatchWaitRemainingInput {
  readonly queueState: MatchmakingQueueState;
  readonly minimumMatchWaitDeadline: number;
  readonly now: number;
}

interface MatchStartDelayInput {
  readonly backendQueueJoinPending: boolean;
  readonly queueState: MatchmakingQueueState | null;
  readonly remainingWaitMs: number;
  readonly recheckMs: number;
}

export function resolveMinimumMatchWaitDeadline({
  matchWaitStartedAt,
  isRestoringActiveSession,
  matchmakingDurationMs
}: MatchWaitDeadlineInput): number {
  return isRestoringActiveSession ? matchWaitStartedAt : matchWaitStartedAt + matchmakingDurationMs;
}

export function resolveMatchCountdownRemainingMs({
  queueState,
  countdownStartedAt,
  matchWaitDeadline,
  now,
  matchmakingDurationMs
}: MatchCountdownRemainingInput): number {
  const sharedRemainingMs = resolveSharedQueueRemainingMs(queueState);
  if (sharedRemainingMs !== null) {
    return sharedRemainingMs;
  }

  const fallbackStartedAt = countdownStartedAt ?? now;
  const fallbackDeadline = fallbackStartedAt + matchmakingDurationMs;
  const deadline = matchWaitDeadline ?? fallbackDeadline;
  return Math.max(0, deadline - now);
}

export function resolveSyncedMatchWaitRemainingMs({
  queueState,
  minimumMatchWaitDeadline,
  now
}: SyncedMatchWaitRemainingInput): number {
  const sharedRemainingMs = resolveSharedQueueRemainingMs(queueState);
  const minimumRemainingMs = Math.max(0, minimumMatchWaitDeadline - now);
  return sharedRemainingMs ?? minimumRemainingMs;
}

export function resolveMatchStartDelayMs({
  backendQueueJoinPending,
  queueState,
  remainingWaitMs,
  recheckMs
}: MatchStartDelayInput): number {
  if (backendQueueJoinPending || !queueState || hasSharedBattleSession(queueState)) {
    return recheckMs;
  }

  return remainingWaitMs + recheckMs;
}
