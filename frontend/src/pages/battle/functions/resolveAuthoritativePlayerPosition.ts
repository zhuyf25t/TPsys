import type { BattleVector2 as Vec2 } from "../../../objects/battle/objects/core/BattleCoreScalars";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";

export function resolveAuthoritativePlayerPosition(
  state: AuthoritativeBattleState | null,
  playerId: string
): Vec2 | null {
  const player = state?.players.find((entry) => entry.playerId === playerId);
  return player ? { x: player.position.x, y: player.position.y } : null;
}
