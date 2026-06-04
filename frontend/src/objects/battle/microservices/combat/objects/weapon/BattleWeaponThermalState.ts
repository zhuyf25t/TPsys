export type BattleWeaponThermalState =
  | { status: "ready" }
  | { status: "overheated"; remainingMs: number };

export function overheatedFlag(value: BattleWeaponThermalState): boolean {
  return value.status === "overheated";
}

export function overheatRemainingMs(value: BattleWeaponThermalState): number {
  return value.status === "overheated" ? Math.max(0, Math.round(value.remainingMs)) : 0;
}

export function overheated(remainingMs: number): BattleWeaponThermalState {
  return remainingMs <= 0 ? { status: "ready" } : { status: "overheated", remainingMs: Math.round(remainingMs) };
}

