import type {
  BattleWeaponInventoryState as WeaponInventory,
  BattleWeaponState as WeaponState
} from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { WeaponKind } from "../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import {
  WEAPON_DEFINITIONS,
  type WeaponDefinition
} from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";

export { WEAPON_DEFINITIONS, type WeaponDefinition };

export function createWeaponState(weaponKind: WeaponKind): WeaponState {
  const definition = WEAPON_DEFINITIONS[weaponKind];

  return {
    weaponKind,
    ammoInMagazine: definition.usesHeat ? 0 : definition.magazineSize,
    magazineSize: definition.magazineSize,
    reserveAmmo: definition.usesHeat ? null : definition.reserveAmmo,
    fireCooldownMs: 0,
    reloadRemainingMs: 0,
    heat: 0,
    overheated: false,
    overheatRemainingMs: 0
  };
}

export function createStarterInventory(): WeaponInventory {
  return {
    currentWeaponIndex: 0,
    weapons: [createWeaponState("Pistol")]
  };
}

export function getCurrentWeapon(inventory: WeaponInventory & { heroId?: string }): WeaponState {
  const weapon = inventory.weapons[inventory.currentWeaponIndex];
  if (!weapon) {
    throw new Error(`Missing weapon for ${inventory.heroId ?? "weapon inventory"}`);
  }

  return weapon;
}

export function findWeaponIndex(weapons: WeaponState[], weaponKind: WeaponKind): number {
  return weapons.findIndex((weapon) => weapon.weaponKind === weaponKind);
}

export function cycleWeaponIndex(currentWeaponIndex: number, weaponCount: number, direction: -1 | 1): number {
  if (weaponCount <= 0) {
    return 0;
  }

  return (currentWeaponIndex + direction + weaponCount) % weaponCount;
}

export function refillWeaponState(existingWeapon: WeaponState): WeaponState {
  const definition = WEAPON_DEFINITIONS[existingWeapon.weaponKind];
  const reserveAmmo =
    existingWeapon.reserveAmmo === null ? null : existingWeapon.reserveAmmo + definition.pickupAmmo;

  return {
    ...existingWeapon,
    ammoInMagazine: definition.usesHeat ? 0 : definition.magazineSize,
    reserveAmmo,
    fireCooldownMs: 0,
    reloadRemainingMs: 0,
    heat: 0,
    overheated: false,
    overheatRemainingMs: 0
  };
}

export function isDisposableWeapon(weaponKind: WeaponKind): boolean {
  return weaponKind !== "Pistol" && weaponKind !== "Gatling";
}

export function isWeaponDepleted(weapon: WeaponState): boolean {
  if (!isDisposableWeapon(weapon.weaponKind)) {
    return false;
  }

  const reserveAmmo = weapon.reserveAmmo ?? 0;
  return weapon.ammoInMagazine <= 0 && reserveAmmo <= 0 && weapon.reloadRemainingMs <= 0;
}
