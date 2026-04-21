import type { Hero, PlayerCommand, WeaponState } from "../../../../domain/types";
import { cycleWeaponIndex, isDisposableWeapon, isWeaponDepleted, WEAPON_DEFINITIONS, type WeaponDefinition } from "../../../../game/weapons";

export type WeaponBlockReason = "switching" | "reloading" | "empty" | "overheated" | "skill";

export interface WeaponSwitchResult {
  switched: boolean;
  previousIndex: number;
  nextIndex: number;
  showNotice: boolean;
}

export interface WeaponSwitchTransactionResult extends WeaponSwitchResult {
  pendingWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface WeaponReloadResult {
  started: boolean;
  reason?: WeaponBlockReason;
}

export interface WeaponFireResult {
  canFire: boolean;
  reason?: WeaponBlockReason | "cooldown";
}

export interface WeaponFireMutation {
  ammoConsumed: boolean;
  heatAdded: number;
  overheated: boolean;
  overheatRemainingMs: number;
  cooldownRemainingMs: number;
}

export interface WeaponSwitchContext {
  player: Hero;
  switchDirection: -1 | 0 | 1;
  weaponSwitchRemainingMs: number;
}

export interface WeaponReloadContext {
  player: Hero;
  weapon: WeaponState;
  weaponSwitchRemainingMs: number;
}

export interface WeaponFireContext {
  player: Hero;
  weapon: WeaponState;
  weaponDefinition: WeaponDefinition;
  command: PlayerCommand;
  weaponSwitchRemainingMs: number;
  playerMotionActive: boolean;
}

export interface WeaponFireResolution {
  result: WeaponFireResult;
  mutation: WeaponFireMutation;
}

export function requestWeaponSwitch(context: WeaponSwitchContext): WeaponSwitchResult {
  const { player, switchDirection, weaponSwitchRemainingMs } = context;
  if (!player.alive || switchDirection === 0 || player.weapons.length <= 1 || weaponSwitchRemainingMs > 0) {
    return {
      switched: false,
      previousIndex: player.currentWeaponIndex,
      nextIndex: player.currentWeaponIndex,
      showNotice: false
    };
  }

  const previousIndex = player.currentWeaponIndex;
  const nextIndex = cycleWeaponIndex(previousIndex, player.weapons.length, switchDirection);
  if (nextIndex === previousIndex) {
    return {
      switched: false,
      previousIndex,
      nextIndex,
      showNotice: false
    };
  }

  return {
    switched: true,
    previousIndex,
    nextIndex,
    showNotice: true
  };
}

export interface BeginWeaponSwitchTransactionContext extends WeaponSwitchContext {
  weaponSwitchMs: number;
}

export function beginWeaponSwitchTransaction(context: BeginWeaponSwitchTransactionContext): WeaponSwitchTransactionResult {
  const switchResult = requestWeaponSwitch(context);
  if (!switchResult.switched) {
    return {
      ...switchResult,
      pendingWeaponIndex: null,
      weaponSwitchRemainingMs: context.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: context.weaponSwitchRemainingMs
    };
  }

  const { player, weaponSwitchMs } = context;
  const currentWeapon = player.weapons[player.currentWeaponIndex];
  if (!currentWeapon) {
    return {
      switched: false,
      previousIndex: player.currentWeaponIndex,
      nextIndex: player.currentWeaponIndex,
      showNotice: false,
      pendingWeaponIndex: null,
      weaponSwitchRemainingMs: context.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: context.weaponSwitchRemainingMs
    };
  }

  currentWeapon.reloadRemaining = 0;
  const nextWeaponIndex = switchResult.nextIndex;
  pruneDepletedDisposableWeapon(player, switchResult.previousIndex);

  return {
    ...switchResult,
    pendingWeaponIndex: nextWeaponIndex,
    weaponSwitchRemainingMs: weaponSwitchMs,
    weaponSwitchTotalMs: weaponSwitchMs
  };
}

export function pruneDepletedDisposableWeapon(player: Hero, previousIndex: number): boolean {
  const previousWeapon = player.weapons[previousIndex];
  if (!previousWeapon || !isDisposableWeapon(previousWeapon.weaponKind) || !isWeaponDepleted(previousWeapon)) {
    return false;
  }

  player.weapons.splice(previousIndex, 1);
  if (player.weapons.length === 0) {
    return true;
  }

  if (previousIndex < player.currentWeaponIndex) {
    player.currentWeaponIndex -= 1;
  }

  player.currentWeaponIndex = Math.max(0, Math.min(player.currentWeaponIndex, player.weapons.length - 1));
  return true;
}

export function requestWeaponReload(context: WeaponReloadContext): WeaponReloadResult {
  const { player, weapon, weaponSwitchRemainingMs } = context;
  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];

  if (
    !player.alive ||
    definition.usesHeat ||
    weaponSwitchRemainingMs > 0 ||
    weapon.reloadRemaining > 0 ||
    weapon.ammoInMagazine >= weapon.magazineSize ||
    weapon.reserveAmmo === null ||
    weapon.reserveAmmo <= 0
  ) {
    return { started: false };
  }

  return { started: true };
}

export function resolveWeaponFire(context: WeaponFireContext): WeaponFireResolution {
  const { weapon, weaponDefinition, command, weaponSwitchRemainingMs, playerMotionActive } = context;
  if (!context.player.alive || context.player.preparedSkill !== null || playerMotionActive || weaponSwitchRemainingMs > 0) {
    return { result: { canFire: false, reason: "switching" }, mutation: { ammoConsumed: false, heatAdded: 0, overheated: false, overheatRemainingMs: 0, cooldownRemainingMs: 0 } };
  }

  if (weapon.reloadRemaining > 0) {
    return { result: { canFire: false, reason: "reloading" }, mutation: { ammoConsumed: false, heatAdded: 0, overheated: false, overheatRemainingMs: 0, cooldownRemainingMs: 0 } };
  }

  const shouldFire =
    weapon.weaponKind === "Gatling"
      ? command.primaryHeld && weapon.cooldownRemaining <= 0 && !weapon.overheated
      : command.primaryJustPressed && weapon.cooldownRemaining <= 0;

  if (!shouldFire) {
    return { result: { canFire: false, reason: "cooldown" }, mutation: { ammoConsumed: false, heatAdded: 0, overheated: false, overheatRemainingMs: 0, cooldownRemainingMs: 0 } };
  }

  if (weapon.weaponKind !== "Gatling" && weapon.ammoInMagazine <= 0) {
    return {
      result: { canFire: false, reason: (weapon.reserveAmmo ?? 0) > 0 ? "reloading" : "empty" },
      mutation: { ammoConsumed: false, heatAdded: 0, overheated: false, overheatRemainingMs: 0, cooldownRemainingMs: 0 }
    };
  }

  if (weapon.weaponKind === "Gatling" && weapon.overheated) {
    return { result: { canFire: false, reason: "overheated" }, mutation: { ammoConsumed: false, heatAdded: 0, overheated: true, overheatRemainingMs: weapon.overheatRemaining, cooldownRemainingMs: 0 } };
  }

  const ammoConsumed = weapon.weaponKind !== "Gatling";
  const heatAdded = weapon.weaponKind === "Gatling" ? weaponDefinition.heatPerShot : 0;
  const heatAfter = weapon.heat + heatAdded;
  const overheated = weapon.weaponKind === "Gatling" && heatAfter >= weaponDefinition.maxHeat;

  if (ammoConsumed) {
    weapon.ammoInMagazine = Math.max(0, weapon.ammoInMagazine - 1);
  }

  if (weapon.weaponKind === "Gatling") {
    weapon.heat = Math.min(weaponDefinition.maxHeat, heatAfter);
    if (overheated) {
      weapon.overheated = true;
      weapon.overheatRemaining = weaponDefinition.overheatLockMs;
    }
  }

  weapon.cooldownRemaining = weaponDefinition.cooldownMs;

  return {
    result: { canFire: true },
    mutation: {
      ammoConsumed,
      heatAdded,
      overheated,
      overheatRemainingMs: weapon.overheatRemaining,
      cooldownRemainingMs: weapon.cooldownRemaining
    }
  };
}
