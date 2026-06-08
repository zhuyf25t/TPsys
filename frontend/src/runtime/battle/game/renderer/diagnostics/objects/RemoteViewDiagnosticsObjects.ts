import type { BattleProjectileState as Projectile } from "../../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { ProjectileKind } from "../../../../../../objects/battle/microservices/combat/objects/projectile/ProjectileKind";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export type RemoteViewInterpolationSource = "interpolated" | "fallback" | "snapshot";

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
  interpolationSource: RemoteViewInterpolationSource | null;
  interpolationSampleCount: number | null;
  interpolationDelayMs: number | null;
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
  interpolation: RemoteViewInterpolationDiagnostics;
  totalDisplayMotionDistance: number;
  totalTargetMotionDistance: number;
  recentSamples: RemoteHeroViewDiagnosticSample[];
  lastSample: RemoteHeroViewDiagnosticSample | null;
}

export interface RemoteViewInterpolationDiagnostics {
  sampleCount: number;
  interpolatedCount: number;
  fallbackCount: number;
  snapshotCount: number;
  unknownCount: number;
  hitRate: number | null;
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
  interpolationSource?: RemoteViewInterpolationSource;
  interpolationSampleCount?: number;
  interpolationDelayMs?: number;
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

export interface RemoteHeroViewDiagnosticsState {
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

export interface SlayDemoBattleDiagnosticsRoot {
  remoteView?: RemoteViewDiagnosticsSnapshot;
  [key: string]: unknown;
}
