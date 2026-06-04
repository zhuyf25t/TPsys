import type { ActiveBattleSession } from "../objects/BattlePageState";

export function mergeCompletedActiveBattleSessions(
  previousSession: ActiveBattleSession,
  nextSession: ActiveBattleSession
): ActiveBattleSession {
  const snapshot =
    nextSession.snapshot.elapsedMs >= previousSession.snapshot.elapsedMs ? nextSession.snapshot : previousSession.snapshot;
  const replayFrames = selectMoreCompleteReplayFrames(previousSession.replayFrames, nextSession.replayFrames);

  return {
    ...nextSession,
    savedAt: Math.max(previousSession.savedAt, nextSession.savedAt),
    snapshot,
    replayFrames,
    localAuthoritativePlayerId:
      nextSession.localAuthoritativePlayerId ?? previousSession.localAuthoritativePlayerId,
    localAuthoritativeTicketId:
      nextSession.localAuthoritativeTicketId ?? previousSession.localAuthoritativeTicketId,
    mapId: nextSession.mapId ?? previousSession.mapId,
    lastReplaySampleElapsed: maxNullableNumber(
      previousSession.lastReplaySampleElapsed,
      nextSession.lastReplaySampleElapsed,
      getLastReplayFrameElapsedMs(replayFrames)
    )
  };
}

function selectMoreCompleteReplayFrames(
  previousFrames: ActiveBattleSession["replayFrames"],
  nextFrames: ActiveBattleSession["replayFrames"]
): ActiveBattleSession["replayFrames"] {
  return isReplayFrameSetMoreComplete(nextFrames, previousFrames) ? nextFrames : previousFrames;
}

function isReplayFrameSetMoreComplete(
  candidateFrames: ActiveBattleSession["replayFrames"],
  currentFrames: ActiveBattleSession["replayFrames"]
): boolean {
  if (candidateFrames.length === 0) {
    return false;
  }
  if (currentFrames.length === 0) {
    return true;
  }

  const candidateLastElapsedMs = getLastReplayFrameElapsedMs(candidateFrames) ?? 0;
  const currentLastElapsedMs = getLastReplayFrameElapsedMs(currentFrames) ?? 0;
  if (candidateLastElapsedMs !== currentLastElapsedMs) {
    return candidateLastElapsedMs > currentLastElapsedMs;
  }

  const candidateSpanMs = getReplayFrameSpanMs(candidateFrames);
  const currentSpanMs = getReplayFrameSpanMs(currentFrames);
  if (candidateSpanMs !== currentSpanMs) {
    return candidateSpanMs > currentSpanMs;
  }

  return candidateFrames.length > currentFrames.length;
}

function getReplayFrameSpanMs(frames: ActiveBattleSession["replayFrames"]): number {
  if (frames.length === 0) {
    return 0;
  }

  const firstFrame = frames[0];
  const lastElapsedMs = getLastReplayFrameElapsedMs(frames) ?? firstFrame.elapsedMs;
  return Math.max(0, lastElapsedMs - firstFrame.elapsedMs);
}

function getLastReplayFrameElapsedMs(frames: ActiveBattleSession["replayFrames"]): number | null {
  const lastFrame = frames[frames.length - 1];
  return lastFrame && Number.isFinite(lastFrame.elapsedMs) ? lastFrame.elapsedMs : null;
}

function maxNullableNumber(...values: Array<number | null>): number | null {
  const finiteValues = values.filter((value): value is number => typeof value === "number" && Number.isFinite(value));
  return finiteValues.length > 0 ? Math.max(...finiteValues) : null;
}
