import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";
import type { BattleWeaponSwitchDirectionStep } from "../../../combat/objects/weapon/BattleWeaponSwitchDirection";

export interface BattlePlayerCommand {
  movement: BattleVector2;
  aim: BattleVector2;
  pointerWorld: BattleVector2;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: BattleWeaponSwitchDirectionStep;
  switchWeaponIndex: number | null;
  toggleBlink: boolean;
  toggleFreeze: boolean;
  castDash: boolean;
  castCritical: boolean;
  reloadPressed: boolean;
}
