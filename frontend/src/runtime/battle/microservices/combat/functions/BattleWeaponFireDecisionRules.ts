import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleWeaponRuleDefinition as WeaponDefinition } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import type { BattleWeaponState as WeaponState } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";

export type WeaponTriggerMode = "pressed" | "held";
export type WeaponAmmoMode = "magazine" | "heat";
export type WeaponBlockReason = "switching" | "reloading" | "empty" | "overheated" | "skill";

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

export interface WeaponReloadContext {
  player: Hero;
  weapon: WeaponState;
  weaponDefinition: WeaponDefinition;
  ammoMode: WeaponAmmoMode;
  weaponSwitchRemainingMs: number;
}

export interface WeaponFireContext {
  player: Hero;
  weapon: WeaponState;
  weaponDefinition: WeaponDefinition;
  ammoMode: WeaponAmmoMode;
  triggerMode: WeaponTriggerMode;
  command: PlayerCommand;
  weaponSwitchRemainingMs: number;
  playerMotionActive: boolean;
}

export interface WeaponFireResolution {
  result: WeaponFireResult;
  mutation: WeaponFireMutation;
}

export function resolveWeaponAmmoMode(
  profile: Pick<{ ammoMode: WeaponAmmoMode }, "ammoMode">,
  definition: Pick<WeaponDefinition, "usesHeat">
): WeaponAmmoMode {
  return profile.ammoMode === "heat" || definition.usesHeat ? "heat" : "magazine";
}

export function requestWeaponReload(context: WeaponReloadContext): WeaponReloadResult {
  const { player, weapon, ammoMode, weaponSwitchRemainingMs } = context;

  if (
    !player.alive ||
    ammoMode === "heat" ||
    weaponSwitchRemainingMs > 0 ||
    weapon.reloadRemainingMs > 0 ||
    weapon.ammoInMagazine >= weapon.magazineSize ||
    weapon.reserveAmmo === null ||
    weapon.reserveAmmo <= 0
  ) {
    return { started: false };
  }

  return { started: true };
}

export function resolveWeaponFire(context: WeaponFireContext): WeaponFireResolution {
  const { weapon, weaponDefinition, command, ammoMode, triggerMode, weaponSwitchRemainingMs, playerMotionActive } = context;
  const usesHeat = ammoMode === "heat";

  if (!context.player.alive || context.player.preparedSkill !== null || playerMotionActive || weaponSwitchRemainingMs > 0) {
    return {
      result: { canFire: false, reason: "switching" },
      mutation: noWeaponFireMutation()
    };
  }

  if (weapon.reloadRemainingMs > 0) {
    return {
      result: { canFire: false, reason: "reloading" },
      mutation: noWeaponFireMutation()
    };
  }

  const triggerActive = triggerMode === "held" ? command.primaryHeld : command.primaryJustPressed;
  const shouldFire = triggerActive && weapon.fireCooldownMs <= 0 && (!usesHeat || !weapon.overheated);

  if (!shouldFire) {
    return {
      result: { canFire: false, reason: "cooldown" },
      mutation: noWeaponFireMutation()
    };
  }

  if (ammoMode === "magazine" && weapon.ammoInMagazine <= 0) {
    return {
      result: { canFire: false, reason: (weapon.reserveAmmo ?? 0) > 0 ? "reloading" : "empty" },
      mutation: noWeaponFireMutation()
    };
  }

  if (usesHeat && weapon.overheated) {
    return {
      result: { canFire: false, reason: "overheated" },
      mutation: {
        ammoConsumed: false,
        heatAdded: 0,
        overheated: true,
        overheatRemainingMs: weapon.overheatRemainingMs,
        cooldownRemainingMs: 0
      }
    };
  }

  const ammoConsumed = ammoMode === "magazine";
  const heatAdded = usesHeat ? weaponDefinition.heatPerShot : 0;
  const heatAfter = weapon.heat + heatAdded;
  const overheated = usesHeat && heatAfter >= weaponDefinition.maxHeat;

  if (ammoConsumed) {
    weapon.ammoInMagazine = Math.max(0, weapon.ammoInMagazine - 1);
  }

  if (usesHeat) {
    weapon.heat = Math.min(weaponDefinition.maxHeat, heatAfter);
    if (overheated) {
      weapon.overheated = true;
      weapon.overheatRemainingMs = weaponDefinition.overheatLockMs;
    }
  }

  weapon.fireCooldownMs = weaponDefinition.cooldownMs;

  return {
    result: { canFire: true },
    mutation: {
      ammoConsumed,
      heatAdded,
      overheated,
      overheatRemainingMs: weapon.overheatRemainingMs,
      cooldownRemainingMs: weapon.fireCooldownMs
    }
  };
}

function noWeaponFireMutation(): WeaponFireMutation {
  return {
    ammoConsumed: false,
    heatAdded: 0,
    overheated: false,
    overheatRemainingMs: 0,
    cooldownRemainingMs: 0
  };
}
