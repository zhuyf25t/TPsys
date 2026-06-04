import type { ActiveBattleSessionOwner } from "../objects/BattlePageState";

interface BattleSessionOwnerInput {
  readonly authenticatedHandle: string | null | undefined;
  readonly authenticatedSessionToken: string | null | undefined;
  readonly loadoutHandle: string;
}

export function resolveBattleSessionOwner({
  authenticatedHandle,
  authenticatedSessionToken,
  loadoutHandle
}: BattleSessionOwnerInput): ActiveBattleSessionOwner {
  const sessionToken = authenticatedSessionToken?.trim() ? authenticatedSessionToken : null;

  return {
    handle: authenticatedHandle ?? loadoutHandle,
    sessionToken
  };
}
