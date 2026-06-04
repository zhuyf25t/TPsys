import type {
  AuthoritativeLocalHeroReplayDiagnosticSample,
  AuthoritativeLocalHeroReplayDiagnosticsCounts,
  AuthoritativeLocalHeroReplayDiagnosticsRecordInput,
  AuthoritativeLocalHeroReplayDiagnosticsSnapshot,
  AuthoritativeLocalHeroReplaySkipReason
} from "../objects/AuthoritativeLocalHeroReplayDiagnosticsObjects";

export interface AuthoritativeLocalHeroReplayDiagnosticsSnapshotInput
  extends AuthoritativeLocalHeroReplayDiagnosticsCounts {
  samples: readonly AuthoritativeLocalHeroReplayDiagnosticSample[];
  sampleWindowSize: number;
}

export function createAuthoritativeLocalHeroReplayDiagnosticSample(
  input: AuthoritativeLocalHeroReplayDiagnosticsRecordInput,
  atMs: number
): AuthoritativeLocalHeroReplayDiagnosticSample {
  return {
    atMs,
    skipped: input.skipped,
    skipReason: input.skipReason,
    unacknowledgedCommandCount: safeAuthoritativeLocalHeroReplayCount(input.unacknowledgedCommandCount),
    lastAcknowledgedCommandSeq: safeAuthoritativeLocalHeroReplaySeq(input.lastAcknowledgedCommandSeq),
    highestLocalCommandSeq: safeAuthoritativeLocalHeroReplaySeq(input.highestLocalCommandSeq),
    totalReplayDeltaMs: safeAuthoritativeLocalHeroReplayNonNegativeNumber(input.totalReplayDeltaMs),
    clampedCommandCount: safeAuthoritativeLocalHeroReplayCount(input.clampedCommandCount),
    rawAuthoritativePositionToReplayTargetDistance: safeAuthoritativeLocalHeroReplayNullableDistance(
      input.rawAuthoritativePositionToReplayTargetDistance
    )
  };
}

export function createAuthoritativeLocalHeroReplayDiagnosticsSnapshot(
  input: AuthoritativeLocalHeroReplayDiagnosticsSnapshotInput
): AuthoritativeLocalHeroReplayDiagnosticsSnapshot {
  return {
    observedCount: input.observedCount,
    replayedCount: input.replayedCount,
    skippedCount: input.skippedCount,
    sampleCount: input.samples.length,
    sampleWindowSize: input.sampleWindowSize,
    skipReasonCounts: { ...input.skipReasonCounts },
    recentSamples: input.samples.map((sample) => ({ ...sample })),
    lastSample: input.samples.length > 0 ? { ...input.samples[input.samples.length - 1] } : null
  };
}

export function createAuthoritativeLocalHeroReplaySkipReasonCounts(): Record<
  AuthoritativeLocalHeroReplaySkipReason,
  number
> {
  return {
    "invalid-input": 0,
    "no-history": 0,
    "no-unacked": 0,
    "invalid-history": 0
  };
}

function safeAuthoritativeLocalHeroReplayCount(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : 0;
}

function safeAuthoritativeLocalHeroReplaySeq(value: number | null): number | null {
  return value !== null && Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : null;
}

function safeAuthoritativeLocalHeroReplayNonNegativeNumber(value: number): number {
  return Number.isFinite(value) ? Math.max(0, value) : 0;
}

function safeAuthoritativeLocalHeroReplayNullableDistance(value: number | null): number | null {
  return value !== null && Number.isFinite(value) && value >= 0 ? value : null;
}
