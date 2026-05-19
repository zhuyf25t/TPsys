import type { Projectile, ProjectileKind, Vec2 } from "../../objects/types";
import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";

const MAX_REMOTE_HERO_SAMPLES = 360;
const MAX_REMOTE_PROJECTILE_BIRTH_SAMPLES = 180;
const MAX_REMOTE_PROJECTILE_TERMINAL_SAMPLES = 180;
const MOTION_DISTANCE_EPSILON = 0.001;

export interface RemoteHeroViewDiagnosticSample {
  sequence: number;
  atMs: number;
  heroId: string;
  displayName: string;
  displayPosition: Vec2;
  targetPosition: Vec2 | null;
  facing: number | null;
  targetFacing: number | null;
  displayToTargetDistance: number | null;
  displayMotionDelta: number;
  targetMotionDelta: number | null;
}

export interface RemoteHeroViewMetricSummary {
  sampleCount: number;
  valueCount: number;
  nullCount: number;
  avg: number | null;
  max: number | null;
  p95: number | null;
  p99: number | null;
}

export interface RemoteHeroViewDiagnostics {
  heroId: string;
  displayName: string;
  firstSeenAtMs: number;
  lastSeenAtMs: number;
  sampleCount: number;
  sampleWindowSize: number;
  displayPosition: Vec2;
  targetPosition: Vec2 | null;
  facing: number | null;
  targetFacing: number | null;
  displayToTargetDistance: number | null;
  motionDistanceDelta: number;
  targetMotionDistanceDelta: number | null;
  displayToTargetDistanceSummary: RemoteHeroViewMetricSummary;
  displayMotionDeltaSummary: RemoteHeroViewMetricSummary;
  targetMotionDeltaSummary: RemoteHeroViewMetricSummary;
  totalDisplayMotionDistance: number;
  totalTargetMotionDistance: number;
  recentSamples: RemoteHeroViewDiagnosticSample[];
  lastSample: RemoteHeroViewDiagnosticSample | null;
}

export interface RemoteProjectileBirthDiagnosticSample {
  sequence: number;
  atMs: number;
  projectileId: string;
  ownerHeroId: string;
  ownerDisplayName: string | null;
  kind: ProjectileKind;
  position: Vec2;
}

export interface RemoteProjectileBirthDiagnostics {
  count: number;
  firstAtMs: number | null;
  lastAtMs: number | null;
  sampleCount: number;
  sampleWindowSize: number;
  recentSamples: RemoteProjectileBirthDiagnosticSample[];
  lastSample: RemoteProjectileBirthDiagnosticSample | null;
}

export interface RemoteProjectileTerminalDiagnosticSample {
  sequence: number;
  atMs: number;
  projectileId: string;
  kind: ProjectileKind;
  source: "server" | "snapshot-diff";
  reason: string | null;
  terminalPosition: Vec2 | null;
  displayPosition: Vec2;
  authoritativePosition: Vec2;
  displayToAuthoritativeDistance: number;
  ttlMs: number;
  maxLifetimeMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
  nearestHeroId: string | null;
  nearestHeroDisplayName: string | null;
  nearestHeroAuthoritativeEdgeDistance: number | null;
  nearestHeroDisplayEdgeDistance: number | null;
  vfxSkipped?: boolean;
  vfxBudgetReason?: string | null;
}

export interface RemoteProjectileTerminalDiagnostics {
  count: number;
  firstAtMs: number | null;
  lastAtMs: number | null;
  sampleCount: number;
  sampleWindowSize: number;
  recentSamples: RemoteProjectileTerminalDiagnosticSample[];
  lastSample: RemoteProjectileTerminalDiagnosticSample | null;
}

export interface RemoteViewDiagnosticsSnapshot {
  heroCount: number;
  heroIds: string[];
  heroes: Record<string, RemoteHeroViewDiagnostics>;
  projectileBirths: RemoteProjectileBirthDiagnostics;
  projectileTerminals: RemoteProjectileTerminalDiagnostics;
}

export interface RemoteHeroViewDiagnosticsRecordInput {
  heroId: string;
  displayName: string;
  displayPosition: Vec2;
  targetPosition?: Vec2;
  facing?: number;
  targetFacing?: number;
}

export interface RemoteProjectileBirthDiagnosticsRecordInput {
  projectile: Pick<Projectile, "projectileId" | "ownerHeroId" | "kind">;
  ownerDisplayName?: string;
  position: Vec2;
}

export interface RemoteProjectileTerminalDiagnosticsRecordInput {
  projectileId: string;
  kind: ProjectileKind;
  source?: "server" | "snapshot-diff";
  reason?: string | null;
  terminalPosition?: Vec2 | null;
  displayPosition: Vec2;
  authoritativePosition: Vec2;
  ttlMs: number;
  maxLifetimeMs: number;
  targetPlayerId?: string | null;
  targetHeroId?: string | null;
  hpBefore?: number | null;
  hpAfter?: number | null;
  damage?: number | null;
  nearestHeroId?: string | null;
  nearestHeroDisplayName?: string | null;
  nearestHeroAuthoritativeEdgeDistance?: number | null;
  nearestHeroDisplayEdgeDistance?: number | null;
  vfxSkipped?: boolean;
  vfxBudgetReason?: string | null;
}

interface RemoteHeroViewDiagnosticsState {
  heroId: string;
  displayName: string;
  firstSeenAtMs: number;
  lastSeenAtMs: number;
  sampleCount: number;
  displayPosition: Vec2;
  targetPosition: Vec2 | null;
  facing: number | null;
  targetFacing: number | null;
  displayToTargetDistance: number | null;
  motionDistanceDelta: number;
  targetMotionDistanceDelta: number | null;
  totalDisplayMotionDistance: number;
  totalTargetMotionDistance: number;
  recentSamples: RemoteHeroViewDiagnosticSample[];
}

interface SlayDemoBattleDiagnosticsRoot {
  remoteView?: RemoteViewDiagnosticsSnapshot;
  [key: string]: unknown;
}

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

  if (!input.heroId || !isFiniteVec2(input.displayPosition)) {
    return;
  }

  const targetPosition = input.targetPosition && isFiniteVec2(input.targetPosition) ? cloneVec2(input.targetPosition) : null;
  const displayPosition = cloneVec2(input.displayPosition);
  const atMs = nowMs();
  const previous = remoteHeroStates.get(input.heroId);
  const displayMotionDelta = previous ? distanceBetween(previous.displayPosition, displayPosition) : 0;
  const targetMotionDelta =
    previous?.targetPosition && targetPosition ? distanceBetween(previous.targetPosition, targetPosition) : null;
  const displayToTargetDistance = targetPosition ? distanceBetween(displayPosition, targetPosition) : null;
  const facing = typeof input.facing === "number" && Number.isFinite(input.facing) ? input.facing : null;
  const targetFacing = typeof input.targetFacing === "number" && Number.isFinite(input.targetFacing) ? input.targetFacing : null;

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
  pushSample(state.recentSamples, {
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
  }, MAX_REMOTE_HERO_SAMPLES);

  remoteHeroStates.set(input.heroId, state);
  publishRemoteViewDiagnostics();
}

export function recordRemoteProjectileBirthDiagnostics(input: RemoteProjectileBirthDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  if (!input.projectile.projectileId || !input.projectile.ownerHeroId || !isFiniteVec2(input.position)) {
    return;
  }

  const atMs = nowMs();
  remoteProjectileBirthCount += 1;
  remoteProjectileBirthFirstAtMs = remoteProjectileBirthFirstAtMs ?? atMs;
  remoteProjectileBirthLastAtMs = atMs;

  pushSample(remoteProjectileBirthSamples, {
    sequence: remoteProjectileBirthCount,
    atMs,
    projectileId: input.projectile.projectileId,
    ownerHeroId: input.projectile.ownerHeroId,
    ownerDisplayName: input.ownerDisplayName ?? null,
    kind: input.projectile.kind,
    position: cloneVec2(input.position)
  }, MAX_REMOTE_PROJECTILE_BIRTH_SAMPLES);

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
    !isFiniteVec2(input.displayPosition) ||
    !isFiniteVec2(input.authoritativePosition) ||
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
    reason: typeof input.reason === "string" && input.reason.trim() ? input.reason.trim() : null,
    terminalPosition: input.terminalPosition && isFiniteVec2(input.terminalPosition) ? cloneVec2(input.terminalPosition) : null,
    displayPosition: cloneVec2(input.displayPosition),
    authoritativePosition: cloneVec2(input.authoritativePosition),
    displayToAuthoritativeDistance: distanceBetween(input.displayPosition, input.authoritativePosition),
    ttlMs: Math.max(0, Math.round(input.ttlMs)),
    maxLifetimeMs: Math.max(0, Math.round(input.maxLifetimeMs)),
    targetPlayerId: normalizeOptionalString(input.targetPlayerId),
    targetHeroId: normalizeOptionalString(input.targetHeroId),
    hpBefore: toFiniteNumberOrNull(input.hpBefore),
    hpAfter: toFiniteNumberOrNull(input.hpAfter),
    damage: toFiniteNumberOrNull(input.damage),
    nearestHeroId: input.nearestHeroId ?? null,
    nearestHeroDisplayName: input.nearestHeroDisplayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: toFiniteNumberOrNull(input.nearestHeroAuthoritativeEdgeDistance),
    nearestHeroDisplayEdgeDistance: toFiniteNumberOrNull(input.nearestHeroDisplayEdgeDistance)
  };

  if (input.vfxSkipped === true) {
    terminalSample.vfxSkipped = true;
    terminalSample.vfxBudgetReason = normalizeOptionalString(input.vfxBudgetReason);
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
      recentSamples: remoteProjectileBirthSamples.map(cloneProjectileBirthSample),
      lastSample:
        remoteProjectileBirthSamples.length > 0
          ? cloneProjectileBirthSample(remoteProjectileBirthSamples[remoteProjectileBirthSamples.length - 1])
          : null
    },
    projectileTerminals: {
      count: remoteProjectileTerminalCount,
      firstAtMs: remoteProjectileTerminalFirstAtMs,
      lastAtMs: remoteProjectileTerminalLastAtMs,
      sampleCount: remoteProjectileTerminalSamples.length,
      sampleWindowSize: MAX_REMOTE_PROJECTILE_TERMINAL_SAMPLES,
      recentSamples: remoteProjectileTerminalSamples.map(cloneProjectileTerminalSample),
      lastSample:
        remoteProjectileTerminalSamples.length > 0
          ? cloneProjectileTerminalSample(remoteProjectileTerminalSamples[remoteProjectileTerminalSamples.length - 1])
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
    displayPosition: cloneVec2(state.displayPosition),
    targetPosition: cloneNullableVec2(state.targetPosition),
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
    recentSamples: state.recentSamples.map(cloneHeroSample),
    lastSample: state.recentSamples.length > 0 ? cloneHeroSample(state.recentSamples[state.recentSamples.length - 1]) : null
  };
}

function summarizeRemoteHeroMetric(
  samples: RemoteHeroViewDiagnosticSample[],
  selectValue: (sample: RemoteHeroViewDiagnosticSample) => number | null
): RemoteHeroViewMetricSummary {
  const values: number[] = [];
  let nullCount = 0;

  samples.forEach((sample) => {
    const value = selectValue(sample);
    if (typeof value === "number" && Number.isFinite(value)) {
      values.push(value);
    } else {
      nullCount += 1;
    }
  });

  if (values.length === 0) {
    return {
      sampleCount: samples.length,
      valueCount: 0,
      nullCount,
      avg: null,
      max: null,
      p95: null,
      p99: null
    };
  }

  const sortedValues = [...values].sort((left, right) => left - right);
  const total = values.reduce((sum, value) => sum + value, 0);

  return {
    sampleCount: samples.length,
    valueCount: values.length,
    nullCount,
    avg: total / values.length,
    max: sortedValues[sortedValues.length - 1],
    p95: percentile(sortedValues, 0.95),
    p99: percentile(sortedValues, 0.99)
  };
}

function percentile(sortedValues: number[], percentileValue: number): number | null {
  if (sortedValues.length === 0) {
    return null;
  }

  const clampedPercentile = Math.min(1, Math.max(0, percentileValue));
  const index = Math.min(sortedValues.length - 1, Math.ceil(sortedValues.length * clampedPercentile) - 1);
  return sortedValues[index];
}

function cloneHeroSample(sample: RemoteHeroViewDiagnosticSample): RemoteHeroViewDiagnosticSample {
  return {
    ...sample,
    displayPosition: cloneVec2(sample.displayPosition),
    targetPosition: cloneNullableVec2(sample.targetPosition)
  };
}

function cloneProjectileBirthSample(sample: RemoteProjectileBirthDiagnosticSample): RemoteProjectileBirthDiagnosticSample {
  return {
    ...sample,
    position: cloneVec2(sample.position)
  };
}

function cloneProjectileTerminalSample(
  sample: RemoteProjectileTerminalDiagnosticSample
): RemoteProjectileTerminalDiagnosticSample {
  return {
    ...sample,
    terminalPosition: cloneNullableVec2(sample.terminalPosition),
    displayPosition: cloneVec2(sample.displayPosition),
    authoritativePosition: cloneVec2(sample.authoritativePosition)
  };
}

function pushSample<TSample>(samples: TSample[], sample: TSample, maxSamples: number): void {
  samples.push(sample);
  if (samples.length > maxSamples) {
    samples.splice(0, samples.length - maxSamples);
  }
}

function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function cloneVec2(position: Vec2): Vec2 {
  return {
    x: position.x,
    y: position.y
  };
}

function cloneNullableVec2(position: Vec2 | null): Vec2 | null {
  return position ? cloneVec2(position) : null;
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

function toFiniteNumberOrNull(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function normalizeOptionalString(value: string | null | undefined): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
