export const AUTHORITATIVE_LOCAL_HERO_REPLAY_SKIP_REASONS = [
  "invalid-input",
  "no-history",
  "no-unacked",
  "invalid-history"
] as const;

export type AuthoritativeLocalHeroReplaySkipReason =
  (typeof AUTHORITATIVE_LOCAL_HERO_REPLAY_SKIP_REASONS)[number];

export interface AuthoritativeLocalHeroReplayDiagnosticSample {
  atMs: number;
  skipped: boolean;
  skipReason: AuthoritativeLocalHeroReplaySkipReason | null;
  unacknowledgedCommandCount: number;
  lastAcknowledgedCommandSeq: number | null;
  highestLocalCommandSeq: number | null;
  totalReplayDeltaMs: number;
  clampedCommandCount: number;
  rawAuthoritativePositionToReplayTargetDistance: number | null;
}

export interface AuthoritativeLocalHeroReplayDiagnosticsSnapshot {
  observedCount: number;
  replayedCount: number;
  skippedCount: number;
  sampleCount: number;
  sampleWindowSize: number;
  skipReasonCounts: Record<AuthoritativeLocalHeroReplaySkipReason, number>;
  recentSamples: AuthoritativeLocalHeroReplayDiagnosticSample[];
  lastSample: AuthoritativeLocalHeroReplayDiagnosticSample | null;
}

export interface AuthoritativeLocalHeroReplayDiagnosticsRecordInput {
  skipped: boolean;
  skipReason: AuthoritativeLocalHeroReplaySkipReason | null;
  unacknowledgedCommandCount: number;
  lastAcknowledgedCommandSeq: number | null;
  highestLocalCommandSeq: number | null;
  totalReplayDeltaMs: number;
  clampedCommandCount: number;
  rawAuthoritativePositionToReplayTargetDistance: number | null;
}

export interface AuthoritativeLocalHeroReplayDiagnosticsCounts {
  observedCount: number;
  replayedCount: number;
  skippedCount: number;
  skipReasonCounts: Record<AuthoritativeLocalHeroReplaySkipReason, number>;
}

export interface SlayDemoAuthoritativeLocalHeroReplayDiagnosticsRoot {
  authoritativeLocalHeroReplay?: AuthoritativeLocalHeroReplayDiagnosticsSnapshot;
  [key: string]: unknown;
}
