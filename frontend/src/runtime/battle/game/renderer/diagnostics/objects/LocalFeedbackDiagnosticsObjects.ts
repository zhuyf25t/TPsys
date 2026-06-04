import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface LocalMotionFeedbackDiagnosticSample {
  sequence: number;
  atMs: number;
  from: Vec2;
  to: Vec2;
  distance: number;
  movement?: Vec2;
  facing?: number;
}

export interface LocalMuzzleFeedbackDiagnosticSample {
  sequence: number;
  atMs: number;
  weaponKind: WeaponKind;
  position: Vec2;
  pointerWorld?: Vec2;
}

export interface LocalFeedbackDiagnosticChannel<TSample> {
  count: number;
  firstAtMs: number | null;
  lastAtMs: number | null;
  sampleCount: number;
  sampleWindowSize: number;
  recentSamples: TSample[];
  lastSample: TSample | null;
}

export interface LocalFeedbackDiagnosticsSnapshot {
  motion: LocalFeedbackDiagnosticChannel<LocalMotionFeedbackDiagnosticSample>;
  muzzle: LocalFeedbackDiagnosticChannel<LocalMuzzleFeedbackDiagnosticSample>;
}

export interface LocalMotionFeedbackDiagnosticsRecordInput {
  from: Vec2;
  to: Vec2;
  movement?: Vec2;
  facing?: number;
}

export interface LocalMuzzleFeedbackDiagnosticsRecordInput {
  weaponKind: WeaponKind;
  position: Vec2;
  pointerWorld?: Vec2;
}

export interface SlayDemoLocalFeedbackDiagnosticsRoot {
  localFeedback?: LocalFeedbackDiagnosticsSnapshot;
  [key: string]: unknown;
}
