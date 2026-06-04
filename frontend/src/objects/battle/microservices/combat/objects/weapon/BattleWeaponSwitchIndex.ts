export type BattleWeaponSwitchIndex = number;

export function battleWeaponSwitchIndexFromWire(value: number): BattleWeaponSwitchIndex | null {
  return Number.isFinite(value) && value >= 0 ? Math.trunc(value) : null;
}

