export type BattleCommandStatus = "applied" | "ignored";

export function isBattleCommandStatus(value: unknown): value is BattleCommandStatus {
  return value === "applied" || value === "ignored";
}

