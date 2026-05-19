import type { Vec2 } from "../../objects/types";
import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import type { LocalAuthoritativeHeroCorrectionMode } from "./localAuthoritativeHeroCorrection";

const MAX_LOCAL_HERO_CORRECTION_SAMPLES = 240;

export interface LocalHeroCorrectionDiagnosticSample {
  atMs: number;
  preDistance: number;
  postDistance: number;
  mode: LocalAuthoritativeHeroCorrectionMode;
  hardSnap: boolean;
  applied: boolean;
  ignoredByDeadzone: boolean;
}

export interface LocalHeroCorrectionDiagnosticDistanceSummary {
  avg: number | null;
  max: number | null;
  p95?: number | null;
  p99?: number | null;
}

export interface LocalHeroCorrectionDiagnosticsSnapshot {
  observedCount: number;
  correctionCount: number;
  appliedCorrectionCount: number;
  ignoredCount: number;
  deadzoneIgnoredCount: number;
  hardSnapCount: number;
  softCorrectionCount: number;
  sampleCount: number;
  sampleWindowSize: number;
  preDistance: LocalHeroCorrectionDiagnosticDistanceSummary & {
    p95: number | null;
    p99: number | null;
  };
  postDistance: LocalHeroCorrectionDiagnosticDistanceSummary;
  recentSamples: LocalHeroCorrectionDiagnosticSample[];
  lastSample: LocalHeroCorrectionDiagnosticSample | null;
}

export interface LocalHeroCorrectionDiagnosticsRecordInput {
  currentPosition: Vec2;
  authoritativePosition: Vec2;
  nextPosition: Vec2;
  hardSnap: boolean;
  applied: boolean;
  ignoredByDeadzone: boolean;
  mode: LocalAuthoritativeHeroCorrectionMode;
}

interface SlayDemoBattleDiagnosticsRoot {
  localHeroCorrection?: LocalHeroCorrectionDiagnosticsSnapshot;
  [key: string]: unknown;
}

let observedCount = 0;
let correctionCount = 0;
let appliedCorrectionCount = 0;
let ignoredCount = 0;
let deadzoneIgnoredCount = 0;
let hardSnapCount = 0;
let softCorrectionCount = 0;
const samples: LocalHeroCorrectionDiagnosticSample[] = [];

publishLocalHeroCorrectionDiagnostics();

export function recordLocalHeroCorrectionDiagnostics(input: LocalHeroCorrectionDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const preDistance = distanceBetween(input.currentPosition, input.authoritativePosition);
  if (!Number.isFinite(preDistance) || preDistance <= 0.001) {
    return;
  }

  const postDistance = distanceBetween(input.nextPosition, input.authoritativePosition);
  if (!Number.isFinite(postDistance)) {
    return;
  }

  const sample: LocalHeroCorrectionDiagnosticSample = {
    atMs: nowMs(),
    preDistance,
    postDistance,
    mode: input.mode,
    hardSnap: input.hardSnap,
    applied: input.applied,
    ignoredByDeadzone: input.ignoredByDeadzone
  };

  observedCount += 1;
  if (input.applied) {
    correctionCount += 1;
    appliedCorrectionCount += 1;
    if (input.hardSnap) {
      hardSnapCount += 1;
    } else {
      softCorrectionCount += 1;
    }
  } else {
    ignoredCount += 1;
    if (input.ignoredByDeadzone) {
      deadzoneIgnoredCount += 1;
    }
  }

  samples.push(sample);
  if (samples.length > MAX_LOCAL_HERO_CORRECTION_SAMPLES) {
    samples.splice(0, samples.length - MAX_LOCAL_HERO_CORRECTION_SAMPLES);
  }

  publishLocalHeroCorrectionDiagnostics();
}

function publishLocalHeroCorrectionDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoBattleDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.localHeroCorrection = createSnapshot();
}

function createSnapshot(): LocalHeroCorrectionDiagnosticsSnapshot {
  return {
    observedCount,
    correctionCount,
    appliedCorrectionCount,
    ignoredCount,
    deadzoneIgnoredCount,
    hardSnapCount,
    softCorrectionCount,
    sampleCount: samples.length,
    sampleWindowSize: MAX_LOCAL_HERO_CORRECTION_SAMPLES,
    preDistance: summarizeDistances(samples.map((sample) => sample.preDistance), true),
    postDistance: summarizeDistances(samples.map((sample) => sample.postDistance), false),
    recentSamples: samples.map((sample) => ({ ...sample })),
    lastSample: samples.length > 0 ? { ...samples[samples.length - 1] } : null
  };
}

function summarizeDistances(
  distances: number[],
  includePercentiles: true
): LocalHeroCorrectionDiagnosticsSnapshot["preDistance"];
function summarizeDistances(
  distances: number[],
  includePercentiles: false
): LocalHeroCorrectionDiagnosticsSnapshot["postDistance"];
function summarizeDistances(
  distances: number[],
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

function percentile(sortedDistances: number[], percentileValue: number): number | null {
  if (sortedDistances.length === 0) {
    return null;
  }

  const index = Math.min(
    sortedDistances.length - 1,
    Math.max(0, Math.ceil(sortedDistances.length * percentileValue) - 1)
  );
  return sortedDistances[index];
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
