import type { GameSnapshot } from "../battle/types";
import type { ReplayFrame, ReplayHeroFrame, ReplayPickupFrame, ReplayProjectileFrame } from "./replayTypes";

export const REPLAY_SAMPLE_INTERVAL_MS = 150;
export const REPLAY_PERSIST_FRAME_LIMIT = 720;
export const REPLAY_CONTINUOUS_MAX_GAP_MS = 1500;
const MAX_PROJECTILES_PER_FRAME = 18;
const REPLAY_PLAYBACK_DUPLICATE_SIGNAL_GAP_MS = 16;

export interface ReplayFrameContinuity {
  frameCount: number;
  durationMs: number;
  maxGapMs: number;
  hasVisualDelta: boolean;
  isContinuous: boolean;
}

/** 中文名：shouldcapture回放帧（shouldCaptureReplayFrame）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function shouldCaptureReplayFrame(lastCapturedElapsedMs: number | null, elapsedMs: number): boolean {
  return lastCapturedElapsedMs === null || elapsedMs - lastCapturedElapsedMs >= REPLAY_SAMPLE_INTERVAL_MS;
}

/** 中文名：构建回放帧（buildReplayFrame）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function buildReplayFrame(snapshot: GameSnapshot): ReplayFrame {
  return {
    elapsedMs: snapshot.elapsedMs,
    worldSize: { ...snapshot.worldSize },
    heroes: snapshot.heroes.map(toReplayHeroFrame),
    projectiles: snapshot.projectiles
      .filter((projectile) => projectile.alive)
      .slice(0, MAX_PROJECTILES_PER_FRAME)
      .map(toReplayProjectileFrame),
    pickups: [...snapshot.weaponPickups, ...snapshot.itemPickups].map(toReplayPickupFrame),
    eventMessages: snapshot.events.slice(-6).map((event) => event.message)
  };
}

/** 中文名：判断是否有meaningful回放frames（hasMeaningfulReplayFrames）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function hasMeaningfulReplayFrames(frames: ReplayFrame[]): boolean {
  if (!Array.isArray(frames) || frames.length < 2) {
    return false;
  }

  const chronologicalFrames = normalizeReplayFrameOrder(frames);
  if (chronologicalFrames.length < 2) {
    return false;
  }

  let previousSignature = buildReplayFramePlaybackSignature(chronologicalFrames[0]);
  for (let index = 1; index < chronologicalFrames.length; index += 1) {
    const currentSignature = buildReplayFramePlaybackSignature(chronologicalFrames[index]);
    if (currentSignature !== previousSignature) {
      return true;
    }

    previousSignature = currentSignature;
  }

  return false;
}

/** 中文名：判断是否有回放帧visualdelta（hasReplayFrameVisualDelta）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function hasReplayFrameVisualDelta(previousFrame: ReplayFrame | null | undefined, nextFrame: ReplayFrame | null | undefined): boolean {
  if (!previousFrame || !nextFrame) {
    return true;
  }

  return buildReplayFramePlaybackSignature(previousFrame) !== buildReplayFramePlaybackSignature(nextFrame);
}

/** 中文名：compact回放frames（compactReplayFrames）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function compactReplayFrames(frames: ReplayFrame[], limit = REPLAY_PERSIST_FRAME_LIMIT): ReplayFrame[] {
  if (!Array.isArray(frames) || frames.length === 0 || limit <= 0) {
    return [];
  }

  const chronologicalFrames = normalizeReplayFrameOrder(frames);
  if (chronologicalFrames.length === 0) {
    return [];
  }

  if (chronologicalFrames.length <= limit) {
    return chronologicalFrames.map((frame) => cloneReplayFrame(frame));
  }

  return sampleReplayFrames(chronologicalFrames, limit);
}

/** 中文名：finalize回放frames（finalizeReplayFrames）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function finalizeReplayFrames(frames: ReplayFrame[], finalSnapshot: GameSnapshot): ReplayFrame[] {
  const finalFrame = buildReplayFrame(finalSnapshot);
  const chronologicalFrames = normalizeReplayFrameOrder(frames);
  const framesBeforeFinal = chronologicalFrames.filter((frame) => frame.elapsedMs < finalFrame.elapsedMs);
  return [...framesBeforeFinal.map((frame) => cloneReplayFrame(frame)), finalFrame];
}

/** 中文名：规范化回放framesforplayback（normalizeReplayFramesForPlayback）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function normalizeReplayFramesForPlayback(frames: ReplayFrame[], limit = REPLAY_PERSIST_FRAME_LIMIT): ReplayFrame[] {
  if (!Array.isArray(frames) || frames.length === 0 || limit <= 0) {
    return [];
  }

  const chronologicalFrames = compactReplayFrames(frames, limit);
  if (chronologicalFrames.length === 0) {
    return [];
  }

  return retimeReplayFramesForPlayback(chronologicalFrames);
}

/** 中文名：analyze回放帧continuity（analyzeReplayFrameContinuity）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function analyzeReplayFrameContinuity(
  frames: ReplayFrame[],
  expectedDurationMs?: number,
  maxAllowedGapMs = REPLAY_CONTINUOUS_MAX_GAP_MS
): ReplayFrameContinuity {
  const chronologicalFrames = normalizeReplayFrameOrder(frames);
  if (chronologicalFrames.length === 0) {
    return {
      frameCount: 0,
      durationMs: Math.max(0, expectedDurationMs ?? 0),
      maxGapMs: 0,
      hasVisualDelta: false,
      isContinuous: false
    };
  }

  let maxGapMs = 0;
  for (let index = 1; index < chronologicalFrames.length; index += 1) {
    maxGapMs = Math.max(maxGapMs, chronologicalFrames[index].elapsedMs - chronologicalFrames[index - 1].elapsedMs);
  }

  const firstElapsedMs = chronologicalFrames[0].elapsedMs;
  const lastElapsedMs = chronologicalFrames[chronologicalFrames.length - 1].elapsedMs;
  const durationMs = Math.max(0, expectedDurationMs ?? lastElapsedMs - firstElapsedMs);
  const startGapMs = Math.max(0, firstElapsedMs);
  const endGapMs = Math.max(0, durationMs > 0 ? durationMs - lastElapsedMs : 0);
  const coverageGapMs = Math.max(maxGapMs, startGapMs, endGapMs);
  const hasVisualDelta = hasMeaningfulReplayFrames(chronologicalFrames);

  return {
    frameCount: chronologicalFrames.length,
    durationMs,
    maxGapMs: coverageGapMs,
    hasVisualDelta,
    isContinuous: hasVisualDelta && chronologicalFrames.length >= 2 && coverageGapMs <= maxAllowedGapMs
  };
}

function normalizeReplayFrameOrder(frames: ReplayFrame[]): ReplayFrame[] {
  const orderedFrames = frames
    .filter((frame) => Number.isFinite(frame.elapsedMs))
    .map((frame, index) => ({ frame, index }))
    .sort((left, right) => left.frame.elapsedMs - right.frame.elapsedMs || left.index - right.index)
    .map(({ frame }) => cloneReplayFrame(frame));

  return stabilizeReplayFrameTimeline(orderedFrames);
}

function stabilizeReplayFrameTimeline(frames: ReplayFrame[]): ReplayFrame[] {
  const stabilizedFrames: ReplayFrame[] = [];

  frames.forEach((frame) => {
    const currentFrame = cloneReplayFrame(frame, Math.max(0, frame.elapsedMs));
    const previousFrame = stabilizedFrames[stabilizedFrames.length - 1];
    if (!previousFrame) {
      stabilizedFrames.push(currentFrame);
      return;
    }

    if (currentFrame.elapsedMs > previousFrame.elapsedMs) {
      stabilizedFrames.push(currentFrame);
      return;
    }

    if (hasReplayFrameVisualDelta(previousFrame, currentFrame)) {
      stabilizedFrames.push(
        cloneReplayFrame(currentFrame, previousFrame.elapsedMs + REPLAY_PLAYBACK_DUPLICATE_SIGNAL_GAP_MS)
      );
      return;
    }

    stabilizedFrames[stabilizedFrames.length - 1] = cloneReplayFrame(currentFrame, previousFrame.elapsedMs);
  });

  return stabilizedFrames;
}

function sampleReplayFrames(frames: ReplayFrame[], limit: number): ReplayFrame[] {
  if (frames.length <= limit) {
    return frames.map((frame) => cloneReplayFrame(frame));
  }

  if (limit === 1) {
    return [cloneReplayFrame(frames[frames.length - 1], 0)];
  }

  const sampled: ReplayFrame[] = [];
  const lastIndex = frames.length - 1;
  const firstElapsed = frames[0].elapsedMs;
  const totalElapsed = Math.max(1, frames[lastIndex].elapsedMs - firstElapsed);
  const bucketCount = limit - 1;
  let nextBucket = 1;

  sampled.push(cloneReplayFrame(frames[0], frames[0].elapsedMs));

  for (let index = 1; index < lastIndex && sampled.length < limit - 1; index += 1) {
    const frame = frames[index];
    const bucket = Math.min(bucketCount - 1, Math.floor(((frame.elapsedMs - firstElapsed) / totalElapsed) * bucketCount));
    if (bucket < nextBucket) {
      continue;
    }

    sampled.push(cloneReplayFrame(frame));
    nextBucket = bucket + 1;
  }

  if (sampled.length < limit) {
    sampled.push(cloneReplayFrame(frames[lastIndex], frames[lastIndex].elapsedMs));
  } else {
    sampled[sampled.length - 1] = cloneReplayFrame(frames[lastIndex], frames[lastIndex].elapsedMs);
  }

  return sampled.slice(0, limit);
}

function retimeReplayFramesForPlayback(frames: ReplayFrame[]): ReplayFrame[] {
  const retimedFrames: ReplayFrame[] = [cloneReplayFrame(frames[0], 0)];
  let elapsedMs = 0;

  for (let index = 1; index < frames.length; index += 1) {
    const previousFrame = frames[index - 1];
    const currentFrame = frames[index];
    elapsedMs += normalizeReplayPlaybackGap(currentFrame.elapsedMs - previousFrame.elapsedMs, previousFrame, currentFrame);
    retimedFrames.push(cloneReplayFrame(currentFrame, elapsedMs));
  }

  return retimedFrames;
}

function normalizeReplayPlaybackGap(rawGapMs: number, previousFrame: ReplayFrame, currentFrame: ReplayFrame): number {
  if (rawGapMs <= 0) {
    return hasReplayFrameVisualDelta(previousFrame, currentFrame) ? REPLAY_PLAYBACK_DUPLICATE_SIGNAL_GAP_MS : 0;
  }

  if (rawGapMs <= REPLAY_CONTINUOUS_MAX_GAP_MS) {
    return rawGapMs;
  }

  return hasReplayFrameVisualDelta(previousFrame, currentFrame) ? REPLAY_CONTINUOUS_MAX_GAP_MS : REPLAY_SAMPLE_INTERVAL_MS;
}

function cloneReplayFrame(frame: ReplayFrame, elapsedMs = frame.elapsedMs): ReplayFrame {
  return {
    elapsedMs,
    worldSize: { ...frame.worldSize },
    heroes: frame.heroes.map((hero) => ({
      ...hero,
      position: { ...hero.position }
    })),
    projectiles: frame.projectiles.map((projectile) => ({
      ...projectile,
      position: { ...projectile.position }
    })),
    pickups: frame.pickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    eventMessages: [...frame.eventMessages]
  };
}

function buildReplayFramePlaybackSignature(frame: ReplayFrame): string {
  return JSON.stringify({
    worldSize: frame.worldSize,
    heroes: frame.heroes.map((hero) => ({
      heroId: hero.heroId,
      displayName: hero.displayName,
      position: hero.position,
      hp: hero.hp,
      maxHp: hero.maxHp,
      alive: hero.alive,
      lifeState: hero.lifeState,
      score: hero.score,
      facing: hero.facing,
      currentWeaponKind: hero.currentWeaponKind,
      eliminatedAtMs: hero.eliminatedAtMs
    })),
    projectiles: frame.projectiles.map((projectile) => ({
      projectileId: projectile.projectileId,
      kind: projectile.kind,
      position: projectile.position,
      facing: projectile.facing,
      alive: projectile.alive,
      ttlMs: projectile.ttlMs,
      splashRadius: projectile.splashRadius
    })),
    pickups: frame.pickups.map((pickup) => ({
      id: pickup.id,
      kind: pickup.kind,
      position: pickup.position,
      available: pickup.available
    })),
    eventMessages: frame.eventMessages.map((message) => message.trim()).filter(Boolean)
  });
}

function toReplayHeroFrame(hero: GameSnapshot["heroes"][number]): ReplayHeroFrame {
  const currentWeaponKind = hero.weapons[hero.currentWeaponIndex]?.weaponKind ?? null;

  return {
    heroId: hero.heroId,
    displayName: hero.displayName,
    position: { ...hero.position },
    hp: hero.hp,
    maxHp: hero.maxHp,
    alive: hero.alive,
    lifeState: hero.lifeState,
    score: hero.score,
    facing: hero.facing,
    currentWeaponKind,
    eliminatedAtMs: hero.eliminatedAtMs
  };
}

function toReplayProjectileFrame(projectile: GameSnapshot["projectiles"][number]): ReplayProjectileFrame {
  return {
    projectileId: projectile.projectileId,
    kind: projectile.kind,
    position: { ...projectile.position },
    facing: projectile.facing,
    alive: projectile.alive,
    ttlMs: projectile.ttlMs,
    splashRadius: projectile.splashRadius
  };
}

function toReplayPickupFrame(
  pickup: GameSnapshot["weaponPickups"][number] | GameSnapshot["itemPickups"][number]
): ReplayPickupFrame {
  return {
    id: "weaponId" in pickup ? pickup.weaponId : pickup.pickupId,
    kind: "weaponId" in pickup ? "weapon" : "medkit",
    position: { ...pickup.position },
    available: pickup.available
  };
}
