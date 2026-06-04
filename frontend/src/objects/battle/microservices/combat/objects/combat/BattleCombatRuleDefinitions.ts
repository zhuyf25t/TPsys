import type { ProjectileKind } from "../projectile/ProjectileKind";
import type { WeaponKind } from "../weapon/WeaponKind";

export interface BattleWeaponRuleDefinition {
  displayName: string;
  projectileKind: ProjectileKind;
  cooldownMs: number;
  reloadMs: number;
  projectileSpeedPerSecond: number;
  projectileDamage: number;
  projectileLifetimeMs: number;
  projectileRadius: number;
  splashRadius: number;
  projectileCount: number;
  spreadRadians: number;
  magazineSize: number;
  reserveAmmo: number;
  pickupAmmo: number;
  usesHeat: boolean;
  maxHeat: number;
  heatPerShot: number;
  coolRatePerSecond: number;
  overheatLockMs: number;
  recoilStrength: number;
}

export type WeaponDefinition = BattleWeaponRuleDefinition;

export const WEAPON_DEFINITIONS: Readonly<Record<WeaponKind, Readonly<BattleWeaponRuleDefinition>>> = {
  Pistol: {
    displayName: "\u624b\u67aa",
    projectileKind: "pistol-bullet",
    cooldownMs: 260,
    reloadMs: 1000,
    projectileSpeedPerSecond: 1400,
    projectileDamage: 12,
    projectileLifetimeMs: 30000,
    projectileRadius: 8,
    splashRadius: 0,
    projectileCount: 1,
    spreadRadians: 0,
    magazineSize: 12,
    reserveAmmo: 48,
    pickupAmmo: 24,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 20
  },
  RocketLauncher: {
    displayName: "\u706b\u7bad\u70ae",
    projectileKind: "rocket",
    cooldownMs: 160,
    reloadMs: 2500,
    projectileSpeedPerSecond: 340,
    projectileDamage: 60,
    projectileLifetimeMs: 30000,
    projectileRadius: 14,
    splashRadius: 132,
    projectileCount: 1,
    spreadRadians: 0,
    magazineSize: 1,
    reserveAmmo: 3,
    pickupAmmo: 1,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 120
  },
  Gatling: {
    displayName: "\u52a0\u7279\u6797",
    projectileKind: "gatling-bullet",
    cooldownMs: 72,
    reloadMs: 0,
    projectileSpeedPerSecond: 980,
    projectileDamage: 5,
    projectileLifetimeMs: 30000,
    projectileRadius: 7,
    splashRadius: 0,
    projectileCount: 1,
    spreadRadians: 0.06,
    magazineSize: 0,
    reserveAmmo: 0,
    pickupAmmo: 0,
    usesHeat: true,
    maxHeat: 100,
    heatPerShot: 8,
    coolRatePerSecond: 32,
    overheatLockMs: 1400,
    recoilStrength: 8
  },
  Shotgun: {
    displayName: "\u9730\u5f39\u67aa",
    projectileKind: "shotgun-pellet",
    cooldownMs: 760,
    reloadMs: 1200,
    projectileSpeedPerSecond: 720,
    projectileDamage: 8,
    projectileLifetimeMs: 30000,
    projectileRadius: 7,
    splashRadius: 0,
    projectileCount: 5,
    spreadRadians: 0.42,
    magazineSize: 6,
    reserveAmmo: 18,
    pickupAmmo: 6,
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
    recoilStrength: 80
  }
};
