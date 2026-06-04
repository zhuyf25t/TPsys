import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { AuthoritativeBattleState } from "../api/BattleAuthoritativeSessionClient";

export function resolveBattleRuntimeLocalLastClientCommandSeq(
  state: AuthoritativeBattleState,
  localPlayerId: string
): number {
  const normalizedLocalPlayerId = localPlayerId.trim();
  if (!normalizedLocalPlayerId) {
    return 0;
  }

  const localPlayer = state.players.find((player) => player.playerId === normalizedLocalPlayerId);
  return localPlayer ? Math.max(0, Math.trunc(localPlayer.lastClientCommandSeq)) : 0;
}

export function resolveBattleRuntimeInitialAuthoritativeLocalHeroId(
  snapshot: GameSnapshot,
  state: AuthoritativeBattleState,
  localPlayerId: string
): string | null {
  const normalizedLocalPlayerId = normalizeBattleRuntimeAuthoritativePlayerId(localPlayerId);
  if (!normalizedLocalPlayerId) {
    return null;
  }

  const localPlayer = state.players.find(
    (player) => normalizeBattleRuntimeAuthoritativePlayerId(player.playerId) === normalizedLocalPlayerId
  );
  if (!localPlayer || !snapshot.heroes.some((hero) => hero.heroId === localPlayer.heroId)) {
    return null;
  }

  return localPlayer.heroId;
}

export function normalizeBattleRuntimeAuthoritativePlayerId(value: string): string {
  return value.trim().toLowerCase();
}
