import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import {
  cloneNullableRemoteViewDiagnosticsVec2,
  cloneRemoteHeroSample,
  cloneRemoteProjectileBirthSample,
  cloneRemoteProjectileTerminalSample,
  cloneRemoteViewDiagnosticsVec2,
  distanceBetweenRemoteViewDiagnosticsVec2,
  isFiniteRemoteViewDiagnosticsVec2,
  normalizeRemoteViewDiagnosticsOptionalString,
  summarizeRemoteHeroMetric,
  toFiniteRemoteViewDiagnosticsNumberOrNull
} from "./functions/RemoteViewDiagnosticsRules";
import type {
  RemoteHeroViewDiagnostics,
  RemoteHeroViewDiagnosticsRecordInput,
  RemoteHeroViewDiagnosticsState,
  RemoteProjectileBirthDiagnosticSample,
  RemoteProjectileBirthDiagnosticsRecordInput,
  RemoteProjectileTerminalDiagnosticSample,
  RemoteProjectileTerminalDiagnosticsRecordInput,
  RemoteViewDiagnosticsSnapshot,
  SlayDemoBattleDiagnosticsRoot
} from "./objects/RemoteViewDiagnosticsObjects";

export type {
  RemoteHeroViewDiagnostics,
  RemoteHeroViewDiagnosticSample,
  RemoteHeroViewDiagnosticsRecordInput,
  RemoteHeroViewMetricSummary,
  RemoteProjectileBirthDiagnosticSample,
  RemoteProjectileBirthDiagnostics,
  RemoteProjectileBirthDiagnosticsRecordInput,
  RemoteProjectileTerminalDiagnosticSample,
  RemoteProjectileTerminalDiagnostics,
  RemoteProjectileTerminalDiagnosticsRecordInput,
  RemoteViewDiagnosticsSnapshot
} from "./objects/RemoteViewDiagnosticsObjects";

const MAX_REMOTE_HERO_SAMPLES = 360;
const MAX_REMOTE_PROJECTILE_BIRTH_SAMPLES = 180;
const MAX_REMOTE_PROJECTILE_TERMINAL_SAMPLES = 180;
const MOTION_DISTANCE_EPSILON = 0.001;

const remoteHeroStates = new Map<string, RemoteHeroViewDiagnosticsState>();
let remoteHeroSampleSequence = 0;
let remoteProjectileBirthCount = 0;
let remoteProjectileBirthFirstAtMs: number | null = null;
let remoteProjectileBirthLastAtMs: number | null = null;
const remoteProjectileBirthSamples: RemoteProjectileBirthDiagnosticSample[] = [];
let remoteProjectileTerminalCount = 0;
let remoteProjectileTerminalFirstAtMs: number | null = null;
let remoteProjectileTerminalLastAtMs: number | null = null;
const remoteProjectileTerminalSamples: RemoteProjectileTerminalDiagnosticSample[] = [];

publishRemoteViewDiagnostics();

export function recordRemoteHeroViewDiagnostics(input: RemoteHeroViewDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  if (!input.heroId || !isFiniteRemoteViewDiagnosticsVec2(input.displayPosition)) {
    return;
  }

  const targetPosition =
    input.targetPosition && isFiniteRemoteViewDiagnosticsVec2(input.targetPosition)
      ? cloneRemoteViewDiagnosticsVec2(input.targetPosition)
      : null;
  const displayPosition = cloneRemoteViewDiagnosticsVec2(input.displayPosition);
  const atMs = nowMs();
  const previous = remoteHeroStates.get(input.heroId);
  const displayMotionDelta = previous
    ? distanceBetweenRemoteViewDiagnosticsVec2(previous.displayPosition, displayPosition)
    : 0;
  const targetMotionDelta =
    previous?.targetPosition && targetPosition
      ? distanceBetweenRemoteViewDiagnosticsVec2(previous.targetPosition, targetPosition)
      : null;
  const displayToTargetDistance = targetPosition
    ? distanceBetweenRemoteViewDiagnosticsVec2(displayPosition, targetPosition)
    : null;
  const facing = typeof input.facing === "number" && Number.isFinite(input.facing) ? input.facing : null;
  const targetFacing =
    typeof input.targetFacing === "number" && Number.isFinite(input.targetFacing) ? input.targetFacing : null;

  const state: RemoteHeroViewDiagnosticsState = previous ?? {
    heroId: input.heroId,
    displayName: input.displayName,
    firstSeenAtMs: atMs,
    lastSeenAtMs: atMs,
    sampleCount: 0,
    displayPosition,
    targetPosition,
    facing,
    targetFacing,
    displayToTargetDistance,
    motionDistanceDelta: 0,
    targetMotionDistanceDelta: null,
    totalDisplayMotionDistance: 0,
    totalTargetMotionDistance: 0,
    recentSamples: []
  };

  state.displayName = input.displayName;
  state.lastSeenAtMs = atMs;
  state.sampleCount += 1;
  state.displayPosition = displayPosition;
  state.targetPosition = targetPosition;
  state.facing = facing;
  state.targetFacing = targetFacing;
  state.displayToTargetDistance = displayToTargetDistance;
  state.motionDistanceDelta = displayMotionDelta;
  state.targetMotionDistanceDelta = targetMotionDelta;
  if (displayMotionDelta > MOTION_DISTANCE_EPSILON) {
    state.totalDisplayMotionDistance += displayMotionDelta;
  }
  if (targetMotionDelta !== null && targetMotionDelta > MOTION_DISTANCE_EPSILON) {
    state.totalTargetMotionDistance += targetMotionDelta;
  }

  remoteHeroSampleSequence += 1;
  pushSample(
    state.recentSamples,
    {
      sequence: remoteHeroSampleSequence,
      atMs,
      heroId: input.heroId,
      displayName: input.displayName,
      displayPosition,
      targetPosition,
      facing,
      targetFacing,
      displayToTargetDistance,
      displayMotionDelta,
      targetMotionDelta
    },
    MAX_REMOTE_HERO_SAMPLES
  );

  remoteHeroStates.set(input.heroId, state);
  publishRemoteViewDiagnostics();
}

export function recordRemoteProjectileBirthDiagnostics(input: RemoteProjectileBirthDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  if (
    !input.projectile.projectileId ||
    !input.projectile.ownerHeroId ||
    !isFiniteRemoteViewDiagnosticsVec2(input.position)
  ) {
    return;
  }

  const atMs = nowMs();
  remoteProjectileBirthCount += 1;
  remoteProjectileBirthFirstAtMs = remoteProjectileBirthFirstAtMs ?? atMs;
  remoteProjectileBirthLastAtMs = atMs;

  pushSample(
    remoteProjectileBirthSamples,
    {
      sequence: remoteProjectileBirthCount,
      atMs,
      projectileId: input.projectile.projectileId,
      ownerHeroId: input.projectile.ownerHeroId,
      ownerDisplayName: input.ownerDisplayName ?? null,
      kind: input.projectile.kind,
      position: cloneRemoteViewDiagnosticsVec2(input.position)
    },
    MAX_REMOTE_PROJECTILE_BIRTH_SAMPLES
  );

  publishRemoteViewDiagnostics();
}

export function shouldRecordRemoteProjectileTerminalDiagnostics(): boolean {
  return isBattleDiagnosticsEnabled();
}

export function recordRemoteProjectileTerminalDiagnostics(input: RemoteProjectileTerminalDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  if (
    !input.projectileId ||
    !isFiniteRemoteViewDiagnosticsVec2(input.displayPosition) ||
    !isFiniteRemoteViewDiagnosticsVec2(input.authoritativePosition) ||
    !Number.isFinite(input.ttlMs) ||
    !Number.isFinite(input.maxLifetimeMs)
  ) {
    return;
  }

  const atMs = nowMs();
  remoteProjectileTerminalCount += 1;
  remoteProjectileTerminalFirstAtMs = remoteProjectileTerminalFirstAtMs ?? atMs;
  remoteProjectileTerminalLastAtMs = atMs;

  const terminalSample: RemoteProjectileTerminalDiagnosticSample = {
    sequence: remoteProjectileTerminalCount,
    atMs,
    projectileId: input.projectileId,
    kind: input.kind,
    source: input.source ?? "snapshot-diff",
    reason:
      typeof input.reason === "string" && input.reason.trim()
        ? input.reason.trim()
        : null,
    terminalPosition:
      input.terminalPosition && isFiniteRemoteViewDiagnosticsVec2(input.terminalPosition)
        ? cloneRemoteViewDiagnosticsVec2(input.terminalPosition)
        : null,
    displayPosition: cloneRemoteViewDiagnosticsVec2(input.displayPosition),
    authoritativePosition: cloneRemoteViewDiagnosticsVec2(input.authoritativePosition),
    displayToAuthoritativeDistance: distanceBetweenRemoteViewDiagnosticsVec2(
      input.displayPosition,
      input.authoritativePosition
    ),
    ttlMs: Math.max(0, Math.round(input.ttlMs)),
    maxLifetimeMs: Math.max(0, Math.round(input.maxLifetimeMs)),
    targetPlayerId: normalizeRemoteViewDiagnosticsOptionalString(input.targetPlayerId),
    targetHeroId: normalizeRemoteViewDiagnosticsOptionalString(input.targetHeroId),
    hpBefore: toFiniteRemoteViewDiagnosticsNumberOrNull(input.hpBefore),
    hpAfter: toFiniteRemoteViewDiagnosticsNumberOrNull(input.hpAfter),
    damage: toFiniteRemoteViewDiagnosticsNumberOrNull(input.damage),
    nearestHeroId: input.nearestHeroId ?? null,
    nearestHeroDisplayName: input.nearestHeroDisplayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: toFiniteRemoteViewDiagnosticsNumberOrNull(
      input.nearestHeroAuthoritativeEdgeDistance
    ),
    nearestHeroDisplayEdgeDistance: toFiniteRemoteViewDiagnosticsNumberOrNull(
      input.nearestHeroDisplayEdgeDistance
    )
  };

  if (input.vfxSkipped === true) {
    terminalSample.vfxSkipped = true;
    terminalSample.vfxBudgetReason = normalizeRemoteViewDiagnosticsOptionalString(input.vfxBudgetReason);
  }

  pushSample(remoteProjectileTerminalSamples, terminalSample, MAX_REMOTE_PROJECTILE_TERMINAL_SAMPLES);
  publishRemoteViewDiagnostics();
}

function publishRemoteViewDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoBattleDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.remoteView = createSnapshot();
}

function createSnapshot(): RemoteViewDiagnosticsSnapshot {
  const heroes: Record<string, RemoteHeroViewDiagnostics> = {};
  const heroIds = [...remoteHeroStates.keys()];
  heroIds.forEach((heroId) => {
    const state = remoteHeroStates.get(heroId);
    if (state) {
      heroes[heroId] = createHeroSnapshot(state);
    }
  });

  return {
    heroCount: heroIds.length,
    heroIds,
    heroes,
    projectileBirths: {
      count: remoteProjectileBirthCount,
      firstAtMs: remoteProjectileBirthFirstAtMs,
      lastAtMs: remoteProjectileBirthLastAtMs,
      sampleCount: remoteProjectileBirthSamples.length,
      sampleWindowSize: MAX_REMOTE_PROJECTILE_BIRTH_SAMPLES,
      recentSamples: remoteProjectileBirthSamples.map(cloneRemoteProjectileBirthSample),
      lastSample:
        remoteProjectileBirthSamples.length > 0
          ? cloneRemoteProjectileBirthSample(remoteProjectileBirthSamples[remoteProjectileBirthSamples.length - 1])
          : null
    },
    projectileTerminals: {
      count: remoteProjectileTerminalCount,
      firstAtMs: remoteProjectileTerminalFirstAtMs,
      lastAtMs: remoteProjectileTerminalLastAtMs,
      sampleCount: remoteProjectileTerminalSamples.length,
      sampleWindowSize: MAX_REMOTE_PROJECTILE_TERMINAL_SAMPLES,
      recentSamples: remoteProjectileTerminalSamples.map(cloneRemoteProjectileTerminalSample),
      lastSample:
        remoteProjectileTerminalSamples.length > 0
          ? cloneRemoteProjectileTerminalSample(remoteProjectileTerminalSamples[remoteProjectileTerminalSamples.length - 1])
          : null
    }
  };
}

function createHeroSnapshot(state: RemoteHeroViewDiagnosticsState): RemoteHeroViewDiagnostics {
  return {
    heroId: state.heroId,
    displayName: state.displayName,
    firstSeenAtMs: state.firstSeenAtMs,
    lastSeenAtMs: state.lastSeenAtMs,
    sampleCount: state.sampleCount,
    sampleWindowSize: MAX_REMOTE_HERO_SAMPLES,
    displayPosition: cloneRemoteViewDiagnosticsVec2(state.displayPosition),
    targetPosition: cloneNullableRemoteViewDiagnosticsVec2(state.targetPosition),
    facing: state.facing,
    targetFacing: state.targetFacing,
    displayToTargetDistance: state.displayToTargetDistance,
    motionDistanceDelta: state.motionDistanceDelta,
    targetMotionDistanceDelta: state.targetMotionDistanceDelta,
    displayToTargetDistanceSummary: summarizeRemoteHeroMetric(
      state.recentSamples,
      (sample) => sample.displayToTargetDistance
    ),
    displayMotionDeltaSummary: summarizeRemoteHeroMetric(state.recentSamples, (sample) => sample.displayMotionDelta),
    targetMotionDeltaSummary: summarizeRemoteHeroMetric(state.recentSamples, (sample) => sample.targetMotionDelta),
    totalDisplayMotionDistance: state.totalDisplayMotionDistance,
    totalTargetMotionDistance: state.totalTargetMotionDistance,
    recentSamples: state.recentSamples.map(cloneRemoteHeroSample),
    lastSample:
      state.recentSamples.length > 0
        ? cloneRemoteHeroSample(state.recentSamples[state.recentSamples.length - 1])
        : null
  };
}

function pushSample<TSample>(samples: TSample[], sample: TSample, maxSamples: number): void {
  samples.push(sample);
  if (samples.length > maxSamples) {
    samples.splice(0, samples.length - maxSamples);
  }
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
