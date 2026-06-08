import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  RemoteHeroDisplayState,
  RemoteHeroInterpolationBuffer,
  RemoteHeroInterpolationSample,
  ResolveRemoteHeroFallbackDisplayStateInput,
  ResolveSmoothedDisplayPositionInput
} from "../objects/RemoteHeroInterpolationObjects";
import { resolveBattleRemoteEntityAdaptiveInterpolationDelayMs } from "./BattleRemoteEntityInterpolationDelayRules";

const AUTHORITATIVE_REMOTE_HERO_HARD_SNAP_DISTANCE = 900;
const AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS = 118;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS = 96;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_MAX_DELAY_MS = 240;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_JITTER_PADDING_MS = 24;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_LATEST_SAMPLE_PADDING_MS = 16;
const AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP = 24;
const AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BREAK_GAP_MS = 260;
const AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON = 0.05;
const AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON = 0.001;

export function createRemoteHeroInterpolationSample(hero: Hero, receivedAtMs: number): RemoteHeroInterpolationSample | null {
  if (!Number.isFinite(receivedAtMs) || !isFiniteVec2(hero.position) || !Number.isFinite(hero.facing)) {
    return null;
  }

  return {
    receivedAtMs,
    position: { x: hero.position.x, y: hero.position.y },
    facing: hero.facing
  };
}

export function recordRemoteHeroInterpolationSample(
  buffer: RemoteHeroInterpolationBuffer,
  sample: RemoteHeroInterpolationSample
): void {
  const lastSample = buffer.samples[buffer.samples.length - 1];
  let recordedSample = sample;

  if (lastSample) {
    const distance = distanceBetween(lastSample.position, sample.position);
    if (!Number.isFinite(distance) || distance >= AUTHORITATIVE_REMOTE_HERO_HARD_SNAP_DISTANCE) {
      buffer.samples = [sample];
      return;
    }

    const sampleGapMs = sample.receivedAtMs - lastSample.receivedAtMs;
    if (Number.isFinite(sampleGapMs) && sampleGapMs > AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BREAK_GAP_MS) {
      buffer.samples = [sample];
      return;
    }

    const facingDelta = Math.abs(wrapAngle(sample.facing - lastSample.facing));
    if (distance <= AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON && facingDelta <= AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON) {
      return;
    }

    if (sample.receivedAtMs <= lastSample.receivedAtMs) {
      recordedSample = {
        ...sample,
        receivedAtMs: lastSample.receivedAtMs + 0.001
      };
    }
  }

  buffer.samples.push(recordedSample);
  if (buffer.samples.length > AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP) {
    buffer.samples.splice(0, buffer.samples.length - AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP);
  }
}

export function resolveInterpolatedRemoteHeroDisplayState(
  buffer: RemoteHeroInterpolationBuffer,
  renderNowMs: number,
  interpolationDelayMs = resolveRemoteHeroInterpolationDelayMs(buffer, renderNowMs)
): RemoteHeroDisplayState | null {
  const renderAtMs = renderNowMs - interpolationDelayMs;
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
    const alpha = durationMs > 0 ? clamp((renderAtMs - from.receivedAtMs) / durationMs, 0, 1) : 1;
    const position = {
      x: linear(from.position.x, to.position.x, alpha),
      y: linear(from.position.y, to.position.y, alpha)
    };
    const facing = interpolateFacing(from.facing, to.facing, alpha);

    if (!isFiniteVec2(position) || !Number.isFinite(facing)) {
      return null;
    }

    return {
      position,
      facing,
      interpolationSource: "interpolated",
      interpolationSampleCount: buffer.samples.length,
      interpolationDelayMs
    };
  }

  return null;
}

export function resolveRemoteHeroFallbackDisplayState({
  currentPosition,
  currentFacing,
  hero,
  deltaMs,
  interpolationDelayMs = AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS
}: ResolveRemoteHeroFallbackDisplayStateInput): RemoteHeroDisplayState {
  const finiteCurrentPosition = resolveFinitePosition(currentPosition);
  const targetPosition = isFiniteVec2(hero.position) ? hero.position : finiteCurrentPosition;
  return {
    position: resolveSmoothedDisplayPosition({
      current: finiteCurrentPosition,
      target: targetPosition,
      deltaMs,
      smoothingMs: AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS,
      snapDistance: AUTHORITATIVE_REMOTE_HERO_HARD_SNAP_DISTANCE
    }),
    facing: Number.isFinite(hero.facing) ? hero.facing : currentFacing,
    interpolationSource: "fallback",
    interpolationSampleCount: 0,
    interpolationDelayMs
  };
}

export function resolveRemoteHeroInterpolationDelayMs(
  buffer: RemoteHeroInterpolationBuffer,
  renderNowMs: number
): number {
  return resolveBattleRemoteEntityAdaptiveInterpolationDelayMs({
    samples: buffer.samples,
    renderNowMs,
    baseDelayMs: AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS,
    maxDelayMs: AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_MAX_DELAY_MS,
    jitterPaddingMs: AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_JITTER_PADDING_MS,
    latestSamplePaddingMs: AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_LATEST_SAMPLE_PADDING_MS
  });
}

export function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

export function resolveFinitePosition(position: Vec2): Vec2 {
  return isFiniteVec2(position) ? position : { x: 0, y: 0 };
}

export function resolveSmoothedDisplayPosition({
  current,
  target,
  deltaMs,
  smoothingMs,
  snapDistance
}: ResolveSmoothedDisplayPositionInput): Vec2 {
  const distance = distanceBetween(current, target);
  if (!Number.isFinite(distance) || distance <= 0.5 || distance >= snapDistance) {
    return target;
  }

  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(0, deltaMs) : 0;
  const alpha = clamp(1 - Math.exp(-safeDeltaMs / Math.max(1, smoothingMs)), 0.08, 0.38);
  return {
    x: current.x + (target.x - current.x) * alpha,
    y: current.y + (target.y - current.y) * alpha
  };
}

function interpolateFacing(from: number, to: number, alpha: number): number {
  if (!Number.isFinite(from) || !Number.isFinite(to) || !Number.isFinite(alpha)) {
    return to;
  }

  return wrapAngle(from + wrapAngle(to - from) * alpha);
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function linear(from: number, to: number, alpha: number): number {
  return from + (to - from) * alpha;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function wrapAngle(angle: number): number {
  const fullTurn = Math.PI * 2;
  let wrapped = (angle + Math.PI) % fullTurn;
  if (wrapped < 0) {
    wrapped += fullTurn;
  }
  return wrapped - Math.PI;
}
