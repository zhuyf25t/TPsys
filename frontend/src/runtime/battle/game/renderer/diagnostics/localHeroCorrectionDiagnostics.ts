import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import {
  createLocalHeroCorrectionDiagnosticSample,
  createLocalHeroCorrectionDiagnosticsSnapshot
} from "./functions/LocalHeroCorrectionDiagnosticsRules";
import type {
  LocalHeroCorrectionDiagnosticSample,
  LocalHeroCorrectionDiagnosticsCounts,
  LocalHeroCorrectionDiagnosticsRecordInput,
  LocalHeroCorrectionDiagnosticsSnapshot,
  SlayDemoLocalHeroCorrectionDiagnosticsRoot
} from "./objects/LocalHeroCorrectionDiagnosticsObjects";

export type {
  LocalHeroCorrectionDiagnosticDistanceSummary,
  LocalHeroCorrectionDiagnosticSample,
  LocalHeroCorrectionDiagnosticsRecordInput,
  LocalHeroCorrectionDiagnosticsSnapshot
} from "./objects/LocalHeroCorrectionDiagnosticsObjects";

const MAX_LOCAL_HERO_CORRECTION_SAMPLES = 240;

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

  const sample = createLocalHeroCorrectionDiagnosticSample(input, nowMs());
  if (!sample) {
    return;
  }

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
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoLocalHeroCorrectionDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.localHeroCorrection = createSnapshot();
}

function createSnapshot(): LocalHeroCorrectionDiagnosticsSnapshot {
  return createLocalHeroCorrectionDiagnosticsSnapshot({
    ...currentCounts(),
    sampleWindowSize: MAX_LOCAL_HERO_CORRECTION_SAMPLES,
    samples
  });
}

function currentCounts(): LocalHeroCorrectionDiagnosticsCounts {
  return {
    observedCount,
    correctionCount,
    appliedCorrectionCount,
    ignoredCount,
    deadzoneIgnoredCount,
    hardSnapCount,
    softCorrectionCount
  };
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
