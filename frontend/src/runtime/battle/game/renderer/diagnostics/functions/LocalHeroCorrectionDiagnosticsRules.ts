import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  LocalHeroCorrectionDiagnosticSample,
  LocalHeroCorrectionDiagnosticsCounts,
  LocalHeroCorrectionDiagnosticsRecordInput,
  LocalHeroCorrectionDiagnosticsSnapshot
} from "../objects/LocalHeroCorrectionDiagnosticsObjects";

const LOCAL_HERO_CORRECTION_DISTANCE_EPSILON = 0.001;

export interface LocalHeroCorrectionDiagnosticsSnapshotInput extends LocalHeroCorrectionDiagnosticsCounts {
  samples: readonly LocalHeroCorrectionDiagnosticSample[];
  sampleWindowSize: number;
}

export function createLocalHeroCorrectionDiagnosticSample(
  input: LocalHeroCorrectionDiagnosticsRecordInput,
  atMs: number
): LocalHeroCorrectionDiagnosticSample | null {
  const preDistance = distanceBetweenLocalHeroCorrectionVec2(input.currentPosition, input.authoritativePosition);
  if (!Number.isFinite(preDistance) || preDistance <= LOCAL_HERO_CORRECTION_DISTANCE_EPSILON) {
    return null;
  }

  const postDistance = distanceBetweenLocalHeroCorrectionVec2(input.nextPosition, input.authoritativePosition);
  if (!Number.isFinite(postDistance)) {
    return null;
  }

  return {
    atMs,
    preDistance,
    postDistance,
    mode: input.mode,
    hardSnap: input.hardSnap,
    applied: input.applied,
    ignoredByDeadzone: input.ignoredByDeadzone
  };
}

export function createLocalHeroCorrectionDiagnosticsSnapshot(
  input: LocalHeroCorrectionDiagnosticsSnapshotInput
): LocalHeroCorrectionDiagnosticsSnapshot {
  return {
    observedCount: input.observedCount,
    correctionCount: input.correctionCount,
    appliedCorrectionCount: input.appliedCorrectionCount,
    ignoredCount: input.ignoredCount,
    deadzoneIgnoredCount: input.deadzoneIgnoredCount,
    hardSnapCount: input.hardSnapCount,
    softCorrectionCount: input.softCorrectionCount,
    sampleCount: input.samples.length,
    sampleWindowSize: input.sampleWindowSize,
    preDistance: summarizeLocalHeroCorrectionDistances(
      input.samples.map((sample) => sample.preDistance),
      true
    ),
    postDistance: summarizeLocalHeroCorrectionDistances(
      input.samples.map((sample) => sample.postDistance),
      false
    ),
    recentSamples: input.samples.map((sample) => ({ ...sample })),
    lastSample: input.samples.length > 0 ? { ...input.samples[input.samples.length - 1] } : null
  };
}

function summarizeLocalHeroCorrectionDistances(
  distances: readonly number[],
  includePercentiles: true
): LocalHeroCorrectionDiagnosticsSnapshot["preDistance"];
function summarizeLocalHeroCorrectionDistances(
  distances: readonly number[],
  includePercentiles: false
): LocalHeroCorrectionDiagnosticsSnapshot["postDistance"];
function summarizeLocalHeroCorrectionDistances(
  distances: readonly number[],
  includePercentiles: boolean
): LocalHeroCorrectionDiagnosticsSnapshot["preDistance"] | LocalHeroCorrectionDiagnosticsSnapshot["postDistance"] {
  if (distances.length === 0) {
    return includePercentiles
      ? { avg: null, max: null, p95: null, p99: null }
      : { avg: null, max: null };
  }

  const sorted = [...distances].sort((left, right) => left - right);
  const sum = distances.reduce((total, value) => total + value, 0);
  const base = {
    avg: sum / distances.length,
    max: sorted[sorted.length - 1]
  };

  if (!includePercentiles) {
    return base;
  }

  return {
    ...base,
    p95: percentile(sorted, 0.95),
    p99: percentile(sorted, 0.99)
  };
}

function percentile(sortedDistances: readonly number[], percentileValue: number): number | null {
  if (sortedDistances.length === 0) {
    return null;
  }

  const index = Math.min(
    sortedDistances.length - 1,
    Math.max(0, Math.ceil(sortedDistances.length * percentileValue) - 1)
  );
  return sortedDistances[index];
}

function distanceBetweenLocalHeroCorrectionVec2(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
