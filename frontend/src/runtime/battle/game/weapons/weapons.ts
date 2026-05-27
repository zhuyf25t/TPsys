import type { WeaponInventory, WeaponKind, WeaponState } from "../../../../objects/battle/types";
import { WEAPON_DEFINITIONS, type WeaponDefinition } from "../assets/battleContentCatalog";

export { WEAPON_DEFINITIONS, type WeaponDefinition };

/** 中文名：创建武器状态（createWeaponState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createWeaponState(weaponKind: WeaponKind): WeaponState {
  const definition = WEAPON_DEFINITIONS[weaponKind];

  return {
    weaponKind,
    ammoInMagazine: definition.usesHeat ? 0 : definition.magazineSize,
    magazineSize: definition.magazineSize,
    reserveAmmo: definition.usesHeat ? null : definition.reserveAmmo,
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
  };
}

/** 中文名：创建starterinventory（createStarterInventory）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createStarterInventory(): WeaponInventory {
  return {
    currentWeaponIndex: 0,
    weapons: [createWeaponState("Pistol")]
  };
}

/** 中文名：查找武器index（findWeaponIndex）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function findWeaponIndex(weapons: WeaponState[], weaponKind: WeaponKind): number {
  return weapons.findIndex((weapon) => weapon.weaponKind === weaponKind);
}

/** 中文名：cycle武器index（cycleWeaponIndex）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function cycleWeaponIndex(currentWeaponIndex: number, weaponCount: number, direction: -1 | 1): number {
  if (weaponCount <= 0) {
    return 0;
  }

  return (currentWeaponIndex + direction + weaponCount) % weaponCount;
}

/** 中文名：refill武器状态（refillWeaponState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function refillWeaponState(existingWeapon: WeaponState): WeaponState {
  const definition = WEAPON_DEFINITIONS[existingWeapon.weaponKind];
  const reserveAmmo =
    existingWeapon.reserveAmmo === null ? null : existingWeapon.reserveAmmo + definition.pickupAmmo;

  return {
    ...existingWeapon,
    ammoInMagazine: definition.usesHeat ? 0 : definition.magazineSize,
    reserveAmmo,
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
  };
}

/** 中文名：判断是否disposable武器（isDisposableWeapon）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isDisposableWeapon(weaponKind: WeaponKind): boolean {
  return weaponKind !== "Pistol" && weaponKind !== "Gatling";
}

/** 中文名：判断是否武器depleted（isWeaponDepleted）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isWeaponDepleted(weapon: WeaponState): boolean {
  if (!isDisposableWeapon(weapon.weaponKind)) {
    return false;
  }

  const reserveAmmo = weapon.reserveAmmo ?? 0;
  return weapon.ammoInMagazine <= 0 && reserveAmmo <= 0 && weapon.reloadRemaining <= 0;
}
