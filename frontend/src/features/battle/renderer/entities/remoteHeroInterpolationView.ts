import Phaser from "phaser";
import type { GameSnapshot, Hero, Vec2 } from "../../../../domain/types";

export interface RemoteHeroInterpolationSample {
  receivedAtMs: number;
  position: Vec2;
  facing: number;
}

export interface RemoteHeroInterpolationBuffer {
  samples: RemoteHeroInterpolationSample[];
}

export interface RemoteHeroDisplayView {
  sprite: Phaser.GameObjects.Image;
}

export interface RemoteHeroInterpolationViewState {
  remoteHeroInterpolationBuffers: Map<string, RemoteHeroInterpolationBuffer>;
  scratchActiveRemoteHeroIds: Set<string>;
  heroViews: Map<string, RemoteHeroDisplayView>;
}

interface RemoteHeroDisplayState {
  position: Vec2;
  facing: number;
}

interface CleanupRemoteHeroInterpolationBuffersInput {
  snapshot: GameSnapshot;
  worldViews: RemoteHeroInterpolationViewState;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
}

interface ResolveRemoteHeroDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: RemoteHeroInterpolationViewState;
  view: RemoteHeroDisplayView;
  hero: Hero;
  deltaMs: number;
}

interface ResolveSmoothedDisplayPositionInput {
  current: Vec2;
  target: Vec2;
  deltaMs: number;
  smoothingMs: number;
  snapDistance: number;
}

const AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE = 150;
// Remote heroes prioritize readability and input feel; keep only a small interpolation cushion over 60ms snapshots.
const AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS = 58;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS = 70;
const AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP = 10;
const AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON = 0.05;
const AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON = 0.001;

export function cleanupRemoteHeroInterpolationBuffers({
  snapshot,
  worldViews,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds
}: CleanupRemoteHeroInterpolationBuffersInput): void {
  if (!sharedAuthoritativeRuntime) {
    worldViews.remoteHeroInterpolationBuffers.clear();
    return;
  }

  const activeRemoteHeroIds = worldViews.scratchActiveRemoteHeroIds;
  activeRemoteHeroIds.clear();
  snapshot.heroes.forEach((hero) => {
    if (
      hero.alive &&
      hero.heroId !== snapshot.playerHeroId &&
      remoteAuthoritativeHeroIds.has(hero.heroId) &&
      worldViews.heroViews.has(hero.heroId)
    ) {
      activeRemoteHeroIds.add(hero.heroId);
    }
  });

  for (const heroId of worldViews.remoteHeroInterpolationBuffers.keys()) {
    if (!activeRemoteHeroIds.has(heroId)) {
      worldViews.remoteHeroInterpolationBuffers.delete(heroId);
    }
  }
}

export function resolveRemoteHeroDisplayState({
  scene,
  worldViews,
  view,
  hero,
  deltaMs
}: ResolveRemoteHeroDisplayStateInput): RemoteHeroDisplayState {
  const receivedAtMs = resolveRenderNowMs(scene);
  const sample = createRemoteHeroInterpolationSample(hero, receivedAtMs);

  if (!sample) {
    return resolveRemoteHeroFallbackDisplayState(view, hero, deltaMs);
  }

  const buffer = getRemoteHeroInterpolationBuffer(worldViews, hero.heroId);
  recordRemoteHeroInterpolationSample(buffer, sample);

  return resolveInterpolatedRemoteHeroDisplayState(buffer, receivedAtMs) ?? resolveRemoteHeroFallbackDisplayState(view, hero, deltaMs);
}

function getRemoteHeroInterpolationBuffer(
  worldViews: RemoteHeroInterpolationViewState,
  heroId: string
): RemoteHeroInterpolationBuffer {
  const existing = worldViews.remoteHeroInterpolationBuffers.get(heroId);
  if (existing) {
    return existing;
  }

  const created: RemoteHeroInterpolationBuffer = { samples: [] };
  worldViews.remoteHeroInterpolationBuffers.set(heroId, created);
  return created;
}

function createRemoteHeroInterpolationSample(hero: Hero, receivedAtMs: number): RemoteHeroInterpolationSample | null {
  if (!Number.isFinite(receivedAtMs) || !isFiniteVec2(hero.position) || !Number.isFinite(hero.facing)) {
    return null;
  }

  return {
    receivedAtMs,
    position: { x: hero.position.x, y: hero.position.y },
    facing: hero.facing
  };
}

function recordRemoteHeroInterpolationSample(
  buffer: RemoteHeroInterpolationBuffer,
  sample: RemoteHeroInterpolationSample
): void {
  const lastSample = buffer.samples[buffer.samples.length - 1];
  if (lastSample) {
    const distance = Phaser.Math.Distance.Between(lastSample.position.x, lastSample.position.y, sample.position.x, sample.position.y);
    if (!Number.isFinite(distance) || distance >= AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE) {
      buffer.samples = [sample];
      return;
    }

    const facingDelta = Math.abs(Phaser.Math.Angle.Wrap(sample.facing - lastSample.facing));
    if (distance <= AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON && facingDelta <= AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON) {
      return;
    }

    if (sample.receivedAtMs <= lastSample.receivedAtMs) {
      sample.receivedAtMs = lastSample.receivedAtMs + 0.001;
    }
  }

  buffer.samples.push(sample);
  if (buffer.samples.length > AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP) {
    buffer.samples.splice(0, buffer.samples.length - AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP);
  }
}

function resolveInterpolatedRemoteHeroDisplayState(
  buffer: RemoteHeroInterpolationBuffer,
  renderNowMs: number
): RemoteHeroDisplayState | null {
  const renderAtMs = renderNowMs - AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS;
  if (!Number.isFinite(renderAtMs) || buffer.samples.length < 2) {
    return null;
  }

  for (let index = 0; index < buffer.samples.length - 1; index += 1) {
    const from = buffer.samples[index];
    const to = buffer.samples[index + 1];
    if (renderAtMs < from.receivedAtMs || renderAtMs > to.receivedAtMs) {
      continue;
    }

    const durationMs = to.receivedAtMs - from.receivedAtMs;
    const alpha = durationMs > 0 ? Phaser.Math.Clamp((renderAtMs - from.receivedAtMs) / durationMs, 0, 1) : 1;
    const position = {
      x: Phaser.Math.Linear(from.position.x, to.position.x, alpha),
      y: Phaser.Math.Linear(from.position.y, to.position.y, alpha)
    };
    const facing = interpolateFacing(from.facing, to.facing, alpha);

    if (!isFiniteVec2(position) || !Number.isFinite(facing)) {
      return null;
    }

    return { position, facing };
  }

  return null;
}

function resolveRemoteHeroFallbackDisplayState(
  view: RemoteHeroDisplayView,
  hero: Hero,
  deltaMs: number
): RemoteHeroDisplayState {
  const currentPosition = resolveFinitePosition({ x: view.sprite.x, y: view.sprite.y });
  const targetPosition = isFiniteVec2(hero.position) ? hero.position : currentPosition;
  return {
    position: resolveSmoothedDisplayPosition({
      current: currentPosition,
      target: targetPosition,
      deltaMs,
      smoothingMs: AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS,
      snapDistance: AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE
    }),
    facing: Number.isFinite(hero.facing) ? hero.facing : view.sprite.rotation
  };
}

function resolveRenderNowMs(scene: Phaser.Scene): number {
  const sceneNowMs = scene.time?.now;
  return Number.isFinite(sceneNowMs) ? sceneNowMs : performance.now();
}

function interpolateFacing(from: number, to: number, alpha: number): number {
  if (!Number.isFinite(from) || !Number.isFinite(to) || !Number.isFinite(alpha)) {
    return to;
  }

  return Phaser.Math.Angle.Wrap(from + Phaser.Math.Angle.Wrap(to - from) * alpha);
}

function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function resolveFinitePosition(position: Vec2): Vec2 {
  return isFiniteVec2(position) ? position : { x: 0, y: 0 };
}

function resolveSmoothedDisplayPosition({
  current,
  target,
  deltaMs,
  smoothingMs,
  snapDistance
}: ResolveSmoothedDisplayPositionInput): Vec2 {
  const distance = Phaser.Math.Distance.Between(current.x, current.y, target.x, target.y);
  if (!Number.isFinite(distance) || distance <= 0.5 || distance >= snapDistance) {
    return target;
  }

  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(0, deltaMs) : 0;
  const alpha = Phaser.Math.Clamp(1 - Math.exp(-safeDeltaMs / Math.max(1, smoothingMs)), 0.12, 0.72);
  return {
    x: current.x + (target.x - current.x) * alpha,
    y: current.y + (target.y - current.y) * alpha
  };
}
