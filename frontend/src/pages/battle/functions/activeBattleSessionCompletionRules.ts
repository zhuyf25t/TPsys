import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { BATTLE_MATCH_DURATION_MS } from "../../../runtime/battle/local/state/battleLocalGateway";
import { isBattleComplete, isLocalPlayerEliminated } from "../../../runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules";
import type { ActiveBattleSession } from "../objects/BattlePageState";

export type StoredSessionFinalizationReason = "complete" | "player-eliminated" | "time-elapsed";

export function advanceActiveBattleSessionElapsed(session: ActiveBattleSession, now: number): ActiveBattleSession {
  if (isSharedAuthoritativeSession(session)) {
    return session;
  }

  const savedAt = Number.isFinite(session.savedAt) ? session.savedAt : now;
  const offlineElapsedMs = Math.max(0, now - savedAt);
  if (offlineElapsedMs <= 0) {
    return session;
  }

  const snapshotElapsedMs = normalizeBattleElapsedMs(session.snapshot.elapsedMs);
  const nextElapsedMs = Math.min(BATTLE_MATCH_DURATION_MS, snapshotElapsedMs + offlineElapsedMs);

  return {
    ...session,
    savedAt: now,
    snapshot: {
      ...session.snapshot,
      elapsedMs: nextElapsedMs
    }
  };
}

export function normalizeCompletedActiveBattleSession(
  session: ActiveBattleSession,
  reason: StoredSessionFinalizationReason
): ActiveBattleSession {
  if (reason !== "time-elapsed" || isBattleComplete(session.snapshot)) {
    return session;
  }

  const snapshot = session.snapshot as GameSnapshot;
  return {
    ...session,
    snapshot: {
      ...snapshot,
      elapsedMs: BATTLE_MATCH_DURATION_MS
    }
  };
}

export function resolveCompletedActiveBattleSessionNormalizationReason(
  session: ActiveBattleSession,
  now: number
): StoredSessionFinalizationReason {
  return resolveStoredSessionFinalizationReason(session, session.snapshot, now) ??
    (isSharedAuthoritativeSession(session) ? "complete" : "time-elapsed");
}

export function resolveStoredSessionFinalizationReason(
  session: Partial<ActiveBattleSession>,
  snapshot: GameSnapshot,
  now: number
): StoredSessionFinalizationReason | null {
  if (isSharedAuthoritativeSession(session)) {
    return null;
  }

  const nullableSnapshot: GameSnapshot | null = snapshot;
  if (isBattleComplete(nullableSnapshot)) {
    return "complete";
  }

  if (isLocalPlayerEliminated(nullableSnapshot)) {
    return "player-eliminated";
  }

  const savedAt = typeof session.savedAt === "number" && Number.isFinite(session.savedAt) ? session.savedAt : null;
  if (savedAt === null) {
    return null;
  }

  const elapsedMs = normalizeBattleElapsedMs(snapshot.elapsedMs);
  const remainingMs = Math.max(0, BATTLE_MATCH_DURATION_MS - elapsedMs);
  return savedAt + remainingMs <= now ? "time-elapsed" : null;
}

export function isSharedAuthoritativeSession(session: Partial<ActiveBattleSession>): boolean {
  return session.sharedAuthoritativeRuntime === true;
}

function normalizeBattleElapsedMs(elapsedMs: number): number {
  if (!Number.isFinite(elapsedMs)) {
    return 0;
  }

  return Math.max(0, Math.min(elapsedMs, BATTLE_MATCH_DURATION_MS));
}
