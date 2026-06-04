export type BattleEventKind = "kill" | "heal" | "pickup" | "respawn";

export function isBattleEventKind(value: unknown): value is BattleEventKind {
  return value === "kill" || value === "heal" || value === "pickup" || value === "respawn";
}

