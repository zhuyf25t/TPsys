import type { GameSnapshot, Vec2 } from "../../../objects/types";
import {
  recordRemoteProjectileTerminalDiagnostics,
  shouldRecordRemoteProjectileTerminalDiagnostics
} from "../remoteViewDiagnostics";
import {
  createTerminalDiagnosticProjectileState,
  resolveNearestTerminalHero,
  type AuthoritativeProjectileTerminalFeedbackState,
  type AuthoritativeProjectileTerminalVfxBudgetReason,
  type ProjectileFeedbackState
} from "./projectileTerminalFeedbackPolicy";

export interface ProjectileTerminalDiagnosticsRecordInput {
  previous: ProjectileFeedbackState;
  projectileId: string;
  snapshot: Pick<GameSnapshot, "heroes">;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
}

export interface AuthoritativeProjectileTerminalDiagnosticsRecordInput {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
  snapshot: Pick<GameSnapshot, "heroes">;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  vfxBudgetReason?: AuthoritativeProjectileTerminalVfxBudgetReason | null;
}

export function shouldRecordProjectileTerminalDiagnostics(): boolean {
  return shouldRecordRemoteProjectileTerminalDiagnostics();
}

export function recordProjectileTerminalDiagnostics({
  previous,
  projectileId,
  snapshot,
  getHeroDisplayPosition
}: ProjectileTerminalDiagnosticsRecordInput): void {
  if (!shouldRecordProjectileTerminalDiagnostics()) {
    return;
  }

  const nearestHero = resolveNearestTerminalHero(previous, snapshot.heroes, getHeroDisplayPosition);
  recordRemoteProjectileTerminalDiagnostics({
    projectileId,
    kind: previous.kind,
    source: "snapshot-diff",
    reason: previous.ttlMs <= 0 ? "ttl" : null,
    terminalPosition: previous.authoritativePosition,
    displayPosition: previous.displayPosition,
    authoritativePosition: previous.authoritativePosition,
    ttlMs: previous.ttlMs,
    maxLifetimeMs: previous.maxLifetimeMs,
    nearestHeroId: nearestHero?.heroId ?? null,
    nearestHeroDisplayName: nearestHero?.displayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
    nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null
  });
}

export function recordAuthoritativeProjectileTerminalDiagnostics({
  terminal,
  previous,
  snapshot,
  getHeroDisplayPosition,
  vfxBudgetReason = null
}: AuthoritativeProjectileTerminalDiagnosticsRecordInput): void {
  if (!shouldRecordProjectileTerminalDiagnostics()) {
    return;
  }

  const terminalProjectile = createTerminalDiagnosticProjectileState(terminal, previous);
  const nearestHero = resolveNearestTerminalHero(terminalProjectile, snapshot.heroes, getHeroDisplayPosition);
  recordRemoteProjectileTerminalDiagnostics({
    projectileId: terminal.projectileId,
    kind: terminal.kind,
    source: "server",
    reason: terminal.reason,
    terminalPosition: terminal.terminalPosition,
    displayPosition: terminalProjectile.displayPosition,
    authoritativePosition: terminal.terminalPosition,
    ttlMs: terminal.ttlAfter,
    maxLifetimeMs: previous?.maxLifetimeMs ?? Math.max(terminal.ttlBefore, terminal.ttlAfter),
    targetPlayerId: terminal.targetPlayerId,
    targetHeroId: terminal.targetHeroId,
    hpBefore: terminal.hpBefore,
    hpAfter: terminal.hpAfter,
    damage: terminal.damage,
    nearestHeroId: nearestHero?.heroId ?? null,
    nearestHeroDisplayName: nearestHero?.displayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
    nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null,
    ...(vfxBudgetReason ? { vfxSkipped: true, vfxBudgetReason } : {})
  });
}
