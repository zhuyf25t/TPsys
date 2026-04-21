import type { GameSnapshot } from "../../domain/types";
import type { ReplayFrame, ReplayHeroFrame, ReplayPickupFrame, ReplayProjectileFrame } from "./replayTypes";

export const REPLAY_SAMPLE_INTERVAL_MS = 150;
export const REPLAY_PERSIST_FRAME_LIMIT = 180;
const MAX_PROJECTILES_PER_FRAME = 18;

export function shouldCaptureReplayFrame(lastCapturedElapsedMs: number | null, elapsedMs: number): boolean {
  return lastCapturedElapsedMs === null || elapsedMs - lastCapturedElapsedMs >= REPLAY_SAMPLE_INTERVAL_MS;
}

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

export function hasMeaningfulReplayFrames(frames: ReplayFrame[]): boolean {
  if (!Array.isArray(frames) || frames.length < 2) {
    return false;
  }

  let previousSignature = buildReplayFrameSignature(frames[0]);
  for (let index = 1; index < frames.length; index += 1) {
    const currentSignature = buildReplayFrameSignature(frames[index]);
    if (currentSignature !== previousSignature) {
      return true;
    }

    previousSignature = currentSignature;
  }

  return false;
}

export function hasReplayFrameVisualDelta(previousFrame: ReplayFrame | null | undefined, nextFrame: ReplayFrame | null | undefined): boolean {
  if (!previousFrame || !nextFrame) {
    return true;
  }

  return buildReplayFrameSignature(previousFrame) !== buildReplayFrameSignature(nextFrame);
}

export function compactReplayFrames(frames: ReplayFrame[], limit = REPLAY_PERSIST_FRAME_LIMIT): ReplayFrame[] {
  if (!Array.isArray(frames) || frames.length === 0 || limit <= 0) {
    return [];
  }

  if (frames.length <= limit) {
    return frames.map(cloneReplayFrame);
  }

  if (limit === 1) {
    return [cloneReplayFrame(frames[frames.length - 1])];
  }

  const compacted: ReplayFrame[] = [];
  const lastIndex = frames.length - 1;
  for (let index = 0; index < limit; index += 1) {
    const sampleIndex = Math.round((index * lastIndex) / (limit - 1));
    const frame = frames[sampleIndex];
    if (!frame) {
      continue;
    }

    const cloned = cloneReplayFrame(frame);
    if (compacted.length === 0 || compacted[compacted.length - 1].elapsedMs !== cloned.elapsedMs) {
      compacted.push(cloned);
    }
  }

  if (compacted.length === 0) {
    return [cloneReplayFrame(frames[lastIndex])];
  }

  compacted[0] = cloneReplayFrame(frames[0]);
  compacted[compacted.length - 1] = cloneReplayFrame(frames[lastIndex]);
  return compacted.slice(0, limit);
}

function cloneReplayFrame(frame: ReplayFrame): ReplayFrame {
  return {
    elapsedMs: frame.elapsedMs,
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

function buildReplayFrameSignature(frame: ReplayFrame): string {
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
    }))
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
