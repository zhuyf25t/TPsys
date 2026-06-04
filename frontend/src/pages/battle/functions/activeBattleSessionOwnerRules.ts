import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { ActiveBattleSessionOwner } from "../objects/BattlePageState";

export type StoredSessionOwnerResolution =
  | { status: "match"; owner: ActiveBattleSessionOwner }
  | { status: "mismatch" }
  | { status: "invalid" };

export function resolveStoredSessionOwner(
  storedOwner: Partial<ActiveBattleSessionOwner> | null | undefined,
  snapshot: GameSnapshot,
  expectedOwner: ActiveBattleSessionOwner
): StoredSessionOwnerResolution {
  const normalizedExpectedOwner = normalizeSessionOwner(expectedOwner);
  if (storedOwner) {
    const normalizedStoredOwner = normalizeStoredSessionOwner(storedOwner);
    if (!normalizedStoredOwner) {
      return { status: "invalid" };
    }

    return isSameSessionOwner(normalizedStoredOwner, normalizedExpectedOwner)
      ? { status: "match", owner: normalizedStoredOwner }
      : { status: "mismatch" };
  }

  if (normalizedExpectedOwner.sessionToken) {
    return { status: "mismatch" };
  }

  return doesLegacySessionMatchOwner(snapshot, normalizedExpectedOwner)
    ? { status: "match", owner: normalizedExpectedOwner }
    : { status: "mismatch" };
}

export function normalizeSessionOwner(owner: ActiveBattleSessionOwner): ActiveBattleSessionOwner {
  return {
    handle: owner.handle.trim(),
    sessionToken: owner.sessionToken?.trim() ? owner.sessionToken.trim() : null
  };
}

export function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function normalizeStoredSessionOwner(
  owner: Partial<ActiveBattleSessionOwner> | null | undefined
): ActiveBattleSessionOwner | null {
  if (!owner) {
    return null;
  }

  const handle = typeof owner.handle === "string" ? owner.handle.trim() : "";
  if (!handle) {
    return null;
  }

  const sessionToken =
    typeof owner.sessionToken === "string" && owner.sessionToken.trim() ? owner.sessionToken.trim() : null;
  return {
    handle,
    sessionToken
  };
}

function isSameSessionOwner(left: ActiveBattleSessionOwner, right: ActiveBattleSessionOwner): boolean {
  if (normalizeHandle(left.handle) !== normalizeHandle(right.handle)) {
    return false;
  }

  if (left.sessionToken || right.sessionToken) {
    return left.sessionToken !== null && right.sessionToken !== null && left.sessionToken === right.sessionToken;
  }

  return true;
}

function doesLegacySessionMatchOwner(snapshot: GameSnapshot, owner: ActiveBattleSessionOwner): boolean {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  return normalizeHandle(player?.displayName ?? "") === normalizeHandle(owner.handle);
}
