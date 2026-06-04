export type BattlePickupAvailability =
  | { status: "available" }
  | { status: "respawning"; remainingMs: number };

export function availableFlag(value: BattlePickupAvailability): boolean {
  return value.status === "available";
}

export function respawnMs(value: BattlePickupAvailability): number {
  return value.status === "respawning" ? Math.max(0, Math.round(value.remainingMs)) : 0;
}

export function respawning(remainingMs: number): BattlePickupAvailability {
  return remainingMs <= 0 ? { status: "available" } : { status: "respawning", remainingMs: Math.round(remainingMs) };
}

export function fromAvailableFlag(available: boolean, remainingMs: number): BattlePickupAvailability {
  return available ? { status: "available" } : respawning(remainingMs);
}

