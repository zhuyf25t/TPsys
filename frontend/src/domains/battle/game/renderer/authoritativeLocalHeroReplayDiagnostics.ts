import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";

const MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES = 240;

export type AuthoritativeLocalHeroReplaySkipReason =
  | "invalid-input"
  | "no-history"
  | "no-unacked"
  | "invalid-history";

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

interface SlayDemoBattleDiagnosticsRoot {
  authoritativeLocalHeroReplay?: AuthoritativeLocalHeroReplayDiagnosticsSnapshot;
  [key: string]: unknown;
}

let observedCount = 0;
let replayedCount = 0;
let skippedCount = 0;
const skipReasonCounts: Record<AuthoritativeLocalHeroReplaySkipReason, number> = {
  "invalid-input": 0,
  "no-history": 0,
  "no-unacked": 0,
  "invalid-history": 0
};
const samples: AuthoritativeLocalHeroReplayDiagnosticSample[] = [];

/** 中文名：记录authoritative本地英雄回放diagnostics（recordAuthoritativeLocalHeroReplayDiagnostics）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function recordAuthoritativeLocalHeroReplayDiagnostics(
  input: AuthoritativeLocalHeroReplayDiagnosticsRecordInput
): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const sample: AuthoritativeLocalHeroReplayDiagnosticSample = {
    atMs: nowMs(),
    skipped: input.skipped,
    skipReason: input.skipReason,
    unacknowledgedCommandCount: safeCount(input.unacknowledgedCommandCount),
    lastAcknowledgedCommandSeq: safeSeq(input.lastAcknowledgedCommandSeq),
    highestLocalCommandSeq: safeSeq(input.highestLocalCommandSeq),
    totalReplayDeltaMs: safeNonNegativeNumber(input.totalReplayDeltaMs),
    clampedCommandCount: safeCount(input.clampedCommandCount),
    rawAuthoritativePositionToReplayTargetDistance: safeNullableDistance(
      input.rawAuthoritativePositionToReplayTargetDistance
    )
  };

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
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoBattleDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.authoritativeLocalHeroReplay = createSnapshot();
}

function createSnapshot(): AuthoritativeLocalHeroReplayDiagnosticsSnapshot {
  return {
    observedCount,
    replayedCount,
    skippedCount,
    sampleCount: samples.length,
    sampleWindowSize: MAX_AUTHORITATIVE_LOCAL_HERO_REPLAY_SAMPLES,
    skipReasonCounts: { ...skipReasonCounts },
    recentSamples: samples.map((sample) => ({ ...sample })),
    lastSample: samples.length > 0 ? { ...samples[samples.length - 1] } : null
  };
}

function safeCount(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : 0;
}

function safeSeq(value: number | null): number | null {
  return value !== null && Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : null;
}

function safeNonNegativeNumber(value: number): number {
  return Number.isFinite(value) ? Math.max(0, value) : 0;
}

function safeNullableDistance(value: number | null): number | null {
  return value !== null && Number.isFinite(value) && value >= 0 ? value : null;
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
