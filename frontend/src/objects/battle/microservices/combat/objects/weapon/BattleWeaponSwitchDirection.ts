export type BattleWeaponSwitchDirection = "previous" | "no_switch" | "next";
export type BattleWeaponSwitchDirectionStep = -1 | 0 | 1;

export function battleWeaponSwitchDirectionFromWire(value: number): BattleWeaponSwitchDirection {
  if (value < 0) {
    return "previous";
  }

  if (value > 0) {
    return "next";
  }

  return "no_switch";
}

export function battleWeaponSwitchDirectionStep(value: BattleWeaponSwitchDirection): BattleWeaponSwitchDirectionStep {
  switch (value) {
    case "previous":
      return -1;
    case "next":
      return 1;
    case "no_switch":
      return 0;
  }
}

