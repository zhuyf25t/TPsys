import type { AmmoCount, BattleWeaponHeat } from "./BattleWeaponScalars";
import type { WeaponKind } from "./WeaponKind";

export interface BattleWeaponState {
  weaponKind: WeaponKind;
  ammoInMagazine: AmmoCount;
  magazineSize: AmmoCount;
  reserveAmmo: AmmoCount | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: BattleWeaponHeat;
  overheated: boolean;
  overheatRemainingMs: number;
}

export interface BattleWeaponInventoryState {
  currentWeaponIndex: number;
  weapons: BattleWeaponState[];
}

