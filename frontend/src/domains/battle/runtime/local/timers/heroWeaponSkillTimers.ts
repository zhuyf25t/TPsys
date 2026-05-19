import type { Hero, WeaponState } from "../../../objects/types";
import { STAMINA_RECOVER_PER_SECOND } from "../../../game/constants";
import { WEAPON_DEFINITIONS } from "../../../game/weapons";

export interface HeroWeaponSkillTimersContext {
  deltaMs: number;
  playerHeroId: string;
  heroes: Hero[];
  pickupNoticeCooldowns: Map<string, number>;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  pendingWeaponIndex: number | null;
}

export interface HeroWeaponSkillTimersResult {
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  pendingWeaponIndex: number | null;
}

export function advanceHeroWeaponSkillTimers(context: HeroWeaponSkillTimersContext): HeroWeaponSkillTimersResult {
  const deltaMs = Math.max(0, context.deltaMs);
  const deltaSeconds = deltaMs / 1000;

  advancePickupNoticeCooldowns(context.pickupNoticeCooldowns, deltaMs);

  context.heroes.forEach((hero) => {
    hero.jumpCooldownMs = Math.max(0, hero.jumpCooldownMs - deltaMs);

    hero.weapons.forEach((weapon) => {
      advanceWeaponTimers(weapon, deltaMs, deltaSeconds);
    });

    hero.skills.forEach((skill) => {
      skill.cooldownMs = Math.max(0, skill.cooldownMs - deltaMs);
      skill.activeMs = Math.max(0, skill.activeMs - deltaMs);
    });

    if (!hero.alive) {
      hero.velocity = { x: 0, y: 0 };
      hero.preparedSkill = null;
      return;
    }

    if (hero.heroId !== context.playerHeroId) {
      hero.stamina = Math.min(hero.maxStamina, hero.stamina + STAMINA_RECOVER_PER_SECOND * deltaSeconds * 0.5);
    }
  });

  const switchState = advanceWeaponSwitchState(context.heroes, context.playerHeroId, deltaMs, context.weaponSwitchRemainingMs, context.weaponSwitchTotalMs, context.pendingWeaponIndex);

  return switchState;
}

function advancePickupNoticeCooldowns(pickupNoticeCooldowns: Map<string, number>, deltaMs: number): void {
  pickupNoticeCooldowns.forEach((remaining, key) => {
    const nextValue = Math.max(0, remaining - deltaMs);
    if (nextValue <= 0) {
      pickupNoticeCooldowns.delete(key);
      return;
    }

    pickupNoticeCooldowns.set(key, nextValue);
  });
}

function advanceWeaponTimers(weapon: WeaponState, deltaMs: number, deltaSeconds: number): void {
  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];
  const previousReloadRemaining = weapon.reloadRemaining;

  weapon.cooldownRemaining = Math.max(0, weapon.cooldownRemaining - deltaMs);
  weapon.reloadRemaining = Math.max(0, weapon.reloadRemaining - deltaMs);
  weapon.overheatRemaining = Math.max(0, weapon.overheatRemaining - deltaMs);

  if (previousReloadRemaining > 0 && weapon.reloadRemaining === 0 && weapon.magazineSize > 0 && weapon.ammoInMagazine < weapon.magazineSize) {
    finishReload(weapon);
  }

  if (weapon.weaponKind === "Gatling") {
    weapon.heat = Math.max(0, weapon.heat - definition.coolRatePerSecond * deltaSeconds);
    if (weapon.overheated && weapon.overheatRemaining === 0) {
      weapon.overheated = false;
    }
    return;
  }

  weapon.heat = 0;
  weapon.overheated = false;
  weapon.overheatRemaining = 0;
}

function finishReload(weapon: WeaponState): void {
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

function advanceWeaponSwitchState(
  heroes: Hero[],
  playerHeroId: string,
  deltaMs: number,
  weaponSwitchRemainingMs: number,
  weaponSwitchTotalMs: number,
  pendingWeaponIndex: number | null
): HeroWeaponSkillTimersResult {
  if (weaponSwitchRemainingMs <= 0) {
    return { weaponSwitchRemainingMs: 0, weaponSwitchTotalMs: 0, pendingWeaponIndex: null };
  }

  const nextRemaining = Math.max(0, weaponSwitchRemainingMs - deltaMs);
  if (nextRemaining > 0) {
    return {
      weaponSwitchRemainingMs: nextRemaining,
      weaponSwitchTotalMs,
      pendingWeaponIndex
    };
  }

  if (pendingWeaponIndex === null) {
    return {
      weaponSwitchRemainingMs: 0,
      weaponSwitchTotalMs: 0,
      pendingWeaponIndex: null
    };
  }

  const player = heroes.find((hero) => hero.heroId === playerHeroId);
  if (player) {
    player.currentWeaponIndex = clampWeaponIndex(pendingWeaponIndex, player.weapons.length);
  }

  return {
    weaponSwitchRemainingMs: 0,
    weaponSwitchTotalMs: 0,
    pendingWeaponIndex: null
  };
}

function clampWeaponIndex(index: number, weaponCount: number): number {
  if (weaponCount <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(index, weaponCount - 1));
}
