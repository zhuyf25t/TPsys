import type { ActiveBattleSession } from "../objects/BattlePageState";

export interface AuthoritativeSessionRestoreIdentity {
  readonly battleId: string;
  readonly localAuthoritativePlayerId: string | null;
  readonly localAuthoritativeTicketId: string | null;
}

export function resolveAuthoritativeSessionRestoreIdentity(
  session: ActiveBattleSession
): AuthoritativeSessionRestoreIdentity {
  return {
    battleId: session.battleId.trim(),
    localAuthoritativePlayerId: normalizeOptionalSessionValue(session.localAuthoritativePlayerId),
    localAuthoritativeTicketId: normalizeOptionalSessionValue(session.localAuthoritativeTicketId)
  };
}

export function isSharedAuthoritativeActiveSession(session: ActiveBattleSession | null): boolean {
  return session?.sharedAuthoritativeRuntime === true;
}

function normalizeOptionalSessionValue(value: string | null | undefined): string | null {
  return value?.trim() ? value.trim() : null;
}
