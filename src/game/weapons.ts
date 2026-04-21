import type { ProjectileKind, WeaponInventory, WeaponKind, WeaponState } from "../domain/types";

export interface WeaponDefinition {
  displayName: string;
  projectileKind: ProjectileKind;
  cooldownMs: number;
  reloadMs: number;
  speed: number;
  damage: number;
  lifetimeMs: number;
  radius: number;
  splashRadius: number;
  pellets: number;
  spreadRadians: number;
  magazineSize: number;
  reserveAmmo: number | null;
  pickupAmmo: number;
  usesHeat: boolean;
  maxHeat: number;
  heatPerShot: number;
  coolRatePerSecond: number;
  overheatLockMs: number;
}

export const WEAPON_DEFINITIONS: Record<WeaponKind, WeaponDefinition> = {
  Pistol: {
    displayName: "手枪",
    projectileKind: "pistol-bullet",
    cooldownMs: 260,
    reloadMs: 1000,
    speed: 920,
    damage: 12,
    lifetimeMs: 900,
    radius: 8,
    splashRadius: 0,
    pellets: 1,
    spreadRadians: 0,
    magazineSize: 12,
    reserveAmmo: 48,
    pickupAmmo: 24,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0
  },
  RocketLauncher: {
    displayName: "火箭炮",
    projectileKind: "rocket",
    cooldownMs: 160,
    reloadMs: 2500,
    speed: 340,
    damage: 60,
    lifetimeMs: 2200,
    radius: 14,
    splashRadius: 132,
    pellets: 1,
    spreadRadians: 0,
    magazineSize: 1,
    reserveAmmo: 3,
    pickupAmmo: 1,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0
  },
  Gatling: {
    displayName: "加特林",
    projectileKind: "gatling-bullet",
    cooldownMs: 72,
    reloadMs: 0,
    speed: 980,
    damage: 5,
    lifetimeMs: 620,
    radius: 7,
    splashRadius: 0,
    pellets: 1,
    spreadRadians: 0.06,
    magazineSize: 0,
    reserveAmmo: null,
    pickupAmmo: 0,
    usesHeat: true,
    maxHeat: 100,
    heatPerShot: 8,
    coolRatePerSecond: 32,
    overheatLockMs: 1400
  },
  Shotgun: {
    displayName: "霰弹枪",
    projectileKind: "shotgun-pellet",
    cooldownMs: 760,
    reloadMs: 1200,
    speed: 720,
    damage: 8,
    lifetimeMs: 330,
    radius: 7,
    splashRadius: 0,
    pellets: 5,
    spreadRadians: 0.42,
    magazineSize: 6,
    reserveAmmo: 18,
    pickupAmmo: 6,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0
  }
};

export function createWeaponState(weaponKind: WeaponKind): WeaponState {
  const definition = WEAPON_DEFINITIONS[weaponKind];

  return {
    weaponKind,
    ammoInMagazine: definition.usesHeat ? 0 : definition.magazineSize,
    magazineSize: definition.magazineSize,
    reserveAmmo: definition.reserveAmmo,
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
  };
}

export function createStarterInventory(): WeaponInventory {
  return {
    currentWeaponIndex: 0,
    weapons: [createWeaponState("Pistol")]
  };
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
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
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
  return weapon.ammoInMagazine <= 0 && reserveAmmo <= 0 && weapon.reloadRemaining <= 0;
}
