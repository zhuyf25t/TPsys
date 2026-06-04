import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  createTerminalDiagnosticProjectileState,
  resolveNearestTerminalHero,
  type AuthoritativeProjectileTerminalFeedbackState,
  type AuthoritativeProjectileTerminalVfxBudgetReason,
  type ProjectileFeedbackState
} from "./BattleProjectileFeedbackRules";

export type BattleProjectileTerminalDiagnosticSource = "server" | "snapshot-diff";

export interface BattleProjectileTerminalDiagnosticPlan {
  projectileId: string;
  kind: ProjectileFeedbackState["kind"];
  source: BattleProjectileTerminalDiagnosticSource;
  reason: string | null;
  terminalPosition: Vec2 | null;
  displayPosition: Vec2;
  authoritativePosition: Vec2;
  ttlMs: number;
  maxLifetimeMs: number;
  targetPlayerId?: string | null;
  targetHeroId?: string | null;
  hpBefore?: number | null;
  hpAfter?: number | null;
  damage?: number | null;
  nearestHeroId: string | null;
  nearestHeroDisplayName: string | null;
  nearestHeroAuthoritativeEdgeDistance: number | null;
  nearestHeroDisplayEdgeDistance: number | null;
  vfxSkipped?: boolean;
  vfxBudgetReason?: AuthoritativeProjectileTerminalVfxBudgetReason | null;
}

export interface BattleProjectileTerminalDiagnosticPlanInput {
  previous: ProjectileFeedbackState;
  projectileId: string;
  heroes: Pick<GameSnapshot, "heroes">["heroes"];
  heroDisplayPositions: ReadonlyMap<string, Vec2>;
}

export interface BattleAuthoritativeProjectileTerminalDiagnosticPlanInput {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
  heroes: Pick<GameSnapshot, "heroes">["heroes"];
  heroDisplayPositions: ReadonlyMap<string, Vec2>;
  vfxBudgetReason?: AuthoritativeProjectileTerminalVfxBudgetReason | null;
}

export function planBattleProjectileTerminalDiagnostic(
  input: BattleProjectileTerminalDiagnosticPlanInput
): BattleProjectileTerminalDiagnosticPlan {
  const nearestHero = resolveNearestTerminalHero(input.previous, input.heroes, (heroId) =>
    input.heroDisplayPositions.get(heroId) ?? null
  );

  return {
    projectileId: input.projectileId,
    kind: input.previous.kind,
    source: "snapshot-diff",
    reason: input.previous.ttlMs <= 0 ? "ttl" : null,
    terminalPosition: cloneVec2(input.previous.authoritativePosition),
    displayPosition: cloneVec2(input.previous.displayPosition),
    authoritativePosition: cloneVec2(input.previous.authoritativePosition),
    ttlMs: input.previous.ttlMs,
    maxLifetimeMs: input.previous.maxLifetimeMs,
    nearestHeroId: nearestHero?.heroId ?? null,
    nearestHeroDisplayName: nearestHero?.displayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
    nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null
  };
}

export function planBattleAuthoritativeProjectileTerminalDiagnostic(
  input: BattleAuthoritativeProjectileTerminalDiagnosticPlanInput
): BattleProjectileTerminalDiagnosticPlan {
  const terminalProjectile = createTerminalDiagnosticProjectileState(input.terminal, input.previous);
  const nearestHero = resolveNearestTerminalHero(terminalProjectile, input.heroes, (heroId) =>
    input.heroDisplayPositions.get(heroId) ?? null
  );
  const vfxBudgetReason = input.vfxBudgetReason ?? null;

  return {
    projectileId: input.terminal.projectileId,
    kind: input.terminal.kind,
    source: "server",
    reason: input.terminal.reason,
    terminalPosition: cloneVec2(input.terminal.terminalPosition),
    displayPosition: cloneVec2(terminalProjectile.displayPosition),
    authoritativePosition: cloneVec2(input.terminal.terminalPosition),
    ttlMs: input.terminal.ttlAfter,
    maxLifetimeMs: input.previous?.maxLifetimeMs ?? Math.max(input.terminal.ttlBefore, input.terminal.ttlAfter),
    targetPlayerId: input.terminal.targetPlayerId,
    targetHeroId: input.terminal.targetHeroId,
    hpBefore: input.terminal.hpBefore,
    hpAfter: input.terminal.hpAfter,
    damage: input.terminal.damage,
    nearestHeroId: nearestHero?.heroId ?? null,
    nearestHeroDisplayName: nearestHero?.displayName ?? null,
    nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
    nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null,
    ...(vfxBudgetReason ? { vfxSkipped: true, vfxBudgetReason } : {})
  };
}

function cloneVec2(position: Vec2): Vec2 {
  return {
    x: position.x,
    y: position.y
  };
}
