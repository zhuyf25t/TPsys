import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { LocalAuthoritativeHeroCorrectionMode } from "../../../../local/movement/BattleLocalAuthoritativeHeroCorrectionRules";

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

export interface LocalHeroCorrectionDiagnosticsCounts {
  observedCount: number;
  correctionCount: number;
  appliedCorrectionCount: number;
  ignoredCount: number;
  deadzoneIgnoredCount: number;
  hardSnapCount: number;
  softCorrectionCount: number;
}

export interface SlayDemoLocalHeroCorrectionDiagnosticsRoot {
  localHeroCorrection?: LocalHeroCorrectionDiagnosticsSnapshot;
  [key: string]: unknown;
}
