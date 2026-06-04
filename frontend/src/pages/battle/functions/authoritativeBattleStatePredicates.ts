import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";

export function resolveAuthoritativeDurationMs(
  sharedAuthoritativeRuntime: boolean,
  state: AuthoritativeBattleState | null
): number | null {
  if (!sharedAuthoritativeRuntime || !state || !Number.isFinite(state.durationMs)) {
    return null;
  }

  return Math.max(1, Math.round(state.durationMs));
}

export function resolveAuthoritativeElapsedMs(
  sharedAuthoritativeRuntime: boolean,
  state: AuthoritativeBattleState | null
): number | null {
  const durationMs = resolveAuthoritativeDurationMs(sharedAuthoritativeRuntime, state);
  if (durationMs === null || !state || !Number.isFinite(state.elapsedMs)) {
    return null;
  }

  return Math.max(0, Math.min(Math.round(state.elapsedMs), durationMs));
}

export function isAuthoritativeDurationExpired(
  sharedAuthoritativeRuntime: boolean,
  state: AuthoritativeBattleState | null
): boolean {
  const durationMs = resolveAuthoritativeDurationMs(sharedAuthoritativeRuntime, state);
  const elapsedMs = resolveAuthoritativeElapsedMs(sharedAuthoritativeRuntime, state);
  return durationMs !== null && elapsedMs !== null && elapsedMs >= durationMs;
}

export function isAuthoritativeBattleFinished(
  sharedAuthoritativeRuntime: boolean,
  state: AuthoritativeBattleState | null
): boolean {
  return Boolean(
    sharedAuthoritativeRuntime &&
      state &&
      (state.phase === "finished" || isAuthoritativeDurationExpired(sharedAuthoritativeRuntime, state))
  );
}

export function isAuthoritativeStateRecoverable(
  state: AuthoritativeBattleState | null,
  expectedBattleId: string
): state is AuthoritativeBattleState {
  if (!state || state.battleId.trim() !== expectedBattleId.trim()) {
    return false;
  }

  if (state.phase === "finished") {
    return false;
  }

  if (!Number.isFinite(state.durationMs) || !Number.isFinite(state.elapsedMs)) {
    return false;
  }

  return Math.round(state.elapsedMs) < Math.max(1, Math.round(state.durationMs));
}

export function isAuthoritativeFinalResultReady(
  state: AuthoritativeBattleState | null
): state is AuthoritativeBattleState {
  return Boolean(state && state.phase === "finished" && state.resultReady && state.replayReady);
}
