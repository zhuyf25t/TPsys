import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleRuntimeAuthoritativeFrame } from "../../session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import {
  resolveAuthoritativeFrameElapsedWatermark,
  type AuthoritativeProjectileTerminalFeedbackState,
  type ProjectileFeedbackState
} from "./BattleProjectileFeedbackRules";

export interface BattleBoundedFeedbackKeyMemoryInput {
  key: string;
  rememberedKeys: ReadonlySet<string>;
  keyQueue: readonly string[];
  limit: number;
}

export interface BattleBoundedFeedbackKeyMemoryUpdate {
  shouldRemember: boolean;
  expiredKeys: string[];
}

export interface BattleReadyAuthoritativeProjectileTerminal {
  terminalKey: string;
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
}

export interface BattleReadyAuthoritativeProjectileTerminalInput {
  queuedTerminals: ReadonlyMap<string, AuthoritativeProjectileTerminalFeedbackState>;
  playedTerminalKeys: ReadonlySet<string>;
  liveProjectileIds: ReadonlySet<string>;
  projectileStates: ReadonlyMap<string, ProjectileFeedbackState>;
}

export interface BattleReadyAuthoritativeProjectileTerminalResolution {
  staleTerminalKeys: string[];
  readyTerminals: BattleReadyAuthoritativeProjectileTerminal[];
}

export interface BattleAuthoritativeProjectileTerminalFreshnessBaselineInput {
  frame: BattleRuntimeAuthoritativeFrame;
  initialized: boolean;
  currentBaselineElapsedMs: number | null;
}

export function collectBattleLiveProjectileIds(
  projectiles: readonly Pick<Projectile, "projectileId">[]
): Set<string> {
  return new Set(projectiles.map((projectile) => projectile.projectileId));
}

export function resolveBattleBoundedFeedbackKeyMemoryUpdate(
  input: BattleBoundedFeedbackKeyMemoryInput
): BattleBoundedFeedbackKeyMemoryUpdate {
  if (input.rememberedKeys.has(input.key)) {
    return {
      shouldRemember: false,
      expiredKeys: []
    };
  }

  const nextQueue = [...input.keyQueue, input.key];
  const overflowCount = Math.max(0, nextQueue.length - Math.max(0, input.limit));
  return {
    shouldRemember: true,
    expiredKeys: nextQueue.slice(0, overflowCount)
  };
}

export function hasBattlePlayedAuthoritativeProjectileTerminalForProjectile(input: {
  playedTerminalKeys: ReadonlySet<string>;
  projectileId: string;
}): boolean {
  for (const terminalKey of input.playedTerminalKeys) {
    if (terminalKey.startsWith(`${input.projectileId}:`)) {
      return true;
    }
  }

  return false;
}

export function resolveBattleReadyAuthoritativeProjectileTerminals(
  input: BattleReadyAuthoritativeProjectileTerminalInput
): BattleReadyAuthoritativeProjectileTerminalResolution {
  const staleTerminalKeys: string[] = [];
  const readyTerminals: BattleReadyAuthoritativeProjectileTerminal[] = [];

  input.queuedTerminals.forEach((terminal, terminalKey) => {
    if (input.playedTerminalKeys.has(terminalKey)) {
      staleTerminalKeys.push(terminalKey);
      return;
    }

    if (input.liveProjectileIds.has(terminal.projectileId)) {
      return;
    }

    readyTerminals.push({
      terminalKey,
      terminal,
      previous: input.projectileStates.get(terminal.projectileId)
    });
  });

  return {
    staleTerminalKeys,
    readyTerminals
  };
}

export function resolveBattleAuthoritativeProjectileTerminalFreshnessBaseline(
  input: BattleAuthoritativeProjectileTerminalFreshnessBaselineInput
): number {
  const frameElapsedMs = resolveAuthoritativeFrameElapsedWatermark(input.frame);
  if (input.initialized && input.currentBaselineElapsedMs !== null) {
    return input.currentBaselineElapsedMs;
  }

  if (input.currentBaselineElapsedMs === null) {
    return frameElapsedMs;
  }

  if (!input.initialized) {
    return Math.max(input.currentBaselineElapsedMs, frameElapsedMs);
  }

  return input.currentBaselineElapsedMs;
}

export function shouldPresentBattleAuthoritativeTerminalTracer(input: {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
  playerHeroId: string;
}): boolean {
  if (input.terminal.ownerHeroId !== input.playerHeroId) {
    return true;
  }

  return input.previous === undefined;
}
