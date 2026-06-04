import type { BattleWeaponState as WeaponState } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import { WEAPON_DEFINITIONS } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";

export interface BattleWeaponTimerAdvanceInput {
  weapon: WeaponState;
  deltaMs: number;
  deltaSeconds: number;
}

export function advanceWeaponTimers(input: BattleWeaponTimerAdvanceInput): void {
  const { weapon, deltaMs, deltaSeconds } = input;
  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];
  const previousReloadRemaining = weapon.reloadRemainingMs;

  weapon.fireCooldownMs = Math.max(0, weapon.fireCooldownMs - deltaMs);
  weapon.reloadRemainingMs = Math.max(0, weapon.reloadRemainingMs - deltaMs);
  weapon.overheatRemainingMs = Math.max(0, weapon.overheatRemainingMs - deltaMs);

  if (previousReloadRemaining > 0 && weapon.reloadRemainingMs === 0 && weapon.magazineSize > 0 && weapon.ammoInMagazine < weapon.magazineSize) {
    finishReload(weapon);
  }

  if (weapon.weaponKind === "Gatling") {
    weapon.heat = Math.max(0, weapon.heat - definition.coolRatePerSecond * deltaSeconds);
    if (weapon.overheated && weapon.overheatRemainingMs === 0) {
      weapon.overheated = false;
    }
    return;
  }

  weapon.heat = 0;
  weapon.overheated = false;
  weapon.overheatRemainingMs = 0;
}

export function finishReload(weapon: WeaponState): void {
  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];
  if (definition.usesHeat || weapon.reserveAmmo === null || weapon.reserveAmmo <= 0) {
    return;
  }

  const missingAmmo = weapon.magazineSize - weapon.ammoInMagazine;
  if (missingAmmo <= 0) {
    return;
  }

  const transferredAmmo = Math.min(missingAmmo, weapon.reserveAmmo);
  weapon.ammoInMagazine += transferredAmmo;
  weapon.reserveAmmo -= transferredAmmo;
}
