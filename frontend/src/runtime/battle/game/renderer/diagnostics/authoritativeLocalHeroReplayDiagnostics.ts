import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import {
  createAuthoritativeLocalHeroReplayDiagnosticSample,
  createAuthoritativeLocalHeroReplayDiagnosticsSnapshot,
  createAuthoritativeLocalHeroReplaySkipReasonCounts
} from "./functions/AuthoritativeLocalHeroReplayDiagnosticsRules";
import type {
  AuthoritativeLocalHeroReplayDiagnosticSample,
  AuthoritativeLocalHeroReplayDiagnosticsCounts,
  AuthoritativeLocalHeroReplayDiagnosticsRecordInput,
  AuthoritativeLocalHeroReplayDiagnosticsSnapshot,
  SlayDemoAuthoritativeLocalHeroReplayDiagnosticsRoot
} from "./objects/AuthoritativeLocalHeroReplayDiagnosticsObjects";

export type {
  AuthoritativeLocalHeroReplayDiagnosticSample,
  AuthoritativeLocalHeroReplayDiagnosticsRecordInput,
  AuthoritativeLocalHeroReplayDiagnosticsSnapshot,
  AuthoritativeLocalHeroReplaySkipReason
} from "./objects/AuthoritativeLocalHeroReplayDiagnosticsObjects";

const MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES = 240;

let observedCount = 0;
let replayedCount = 0;
let skippedCount = 0;
const skipReasonCounts = createAuthoritativeLocalHeroReplaySkipReasonCounts();
const samples: AuthoritativeLocalHeroReplayDiagnosticSample[] = [];

export function recordAuthoritativeLocalHeroReplayDiagnostics(
  input: AuthoritativeLocalHeroReplayDiagnosticsRecordInput
): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const sample = createAuthoritativeLocalHeroReplayDiagnosticSample(input, nowMs());

  observedCount += 1;
  if (sample.skipped) {
    skippedCount += 1;
    if (sample.skipReason) {
      skipReasonCounts[sample.skipReason] += 1;
    }
  } else {
    replayedCount += 1;
  }

  samples.push(sample);
  if (samples.length > MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES) {
    samples.splice(0, samples.length - MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES);
  }

  publishAuthoritativeLocalHeroReplayDiagnostics();
}

function publishAuthoritativeLocalHeroReplayDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoAuthoritativeLocalHeroReplayDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.authoritativeLocalHeroReplay = createSnapshot();
}

function createSnapshot(): AuthoritativeLocalHeroReplayDiagnosticsSnapshot {
  return createAuthoritativeLocalHeroReplayDiagnosticsSnapshot({
    ...currentCounts(),
    sampleWindowSize: MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES,
    samples
  });
}

function currentCounts(): AuthoritativeLocalHeroReplayDiagnosticsCounts {
  return {
    observedCount,
    replayedCount,
    skippedCount,
    skipReasonCounts
  };
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
