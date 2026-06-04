import type { DurationMillis, ElapsedMillis } from "../../../../objects/core/BattleCoreScalars";

export type BattlePlayerLifeState =
  | { kind: "Alive" }
  | { kind: "Eliminated"; eliminatedAtMs: ElapsedMillis | null; respawnMs: DurationMillis };

export const aliveBattlePlayerLifeState: BattlePlayerLifeState = { kind: "Alive" };

export function battlePlayerLifeStateFromAliveFlag(input: {
  alive: boolean;
  eliminatedAtMs: ElapsedMillis | null;
  respawnMs: DurationMillis;
}): BattlePlayerLifeState {
  if (input.alive) {
    return aliveBattlePlayerLifeState;
  }

  return {
    kind: "Eliminated",
    eliminatedAtMs: input.eliminatedAtMs,
    respawnMs: Math.max(0, input.respawnMs)
  };
}

export function battlePlayerLifeStateAliveFlag(value: BattlePlayerLifeState): boolean {
  return value.kind === "Alive";
}

export function battlePlayerLifeStateEliminatedAtMs(value: BattlePlayerLifeState): ElapsedMillis | null {
  return value.kind === "Eliminated" ? value.eliminatedAtMs : null;
}

export function battlePlayerLifeStateRespawnMs(value: BattlePlayerLifeState): DurationMillis {
  return value.kind === "Eliminated" ? Math.max(0, value.respawnMs) : 0;
}

