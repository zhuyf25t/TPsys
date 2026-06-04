import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { STAMINA_RECOVER_PER_SECOND } from "../../game/objects/BattleGameConstants";
import { advanceWeaponTimers } from "../../microservices/combat/functions/BattleWeaponTimerRules";
import { advanceWeaponSwitchTimerState } from "../../microservices/combat/functions/BattleWeaponSwitchRules";
import { advanceBattleSkillTimer } from "../../microservices/abilities/functions/BattleSkillStateRules";
import {
  advanceBattleHeroJumpCooldownMs,
  recoverBattleNonLocalHeroStamina,
  resolveBattleDeadHeroRuntimeState
} from "../../microservices/actors/functions/BattlePlayerRuntimeRules";

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
    hero.jumpCooldownMs = advanceBattleHeroJumpCooldownMs(hero.jumpCooldownMs, deltaMs);

    hero.weapons.forEach((weapon) => {
      advanceWeaponTimers({ weapon, deltaMs, deltaSeconds });
    });

    hero.skills = hero.skills.map((skill) => advanceBattleSkillTimer(skill, deltaMs));

    const deadRuntimeState = resolveBattleDeadHeroRuntimeState(hero);
    if (deadRuntimeState) {
      Object.assign(hero, deadRuntimeState);
      return;
    }

    hero.stamina = recoverBattleNonLocalHeroStamina({
      hero,
      playerHeroId: context.playerHeroId,
      deltaMs,
      staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND
    });
  });

  const player = context.heroes.find((hero) => hero.heroId === context.playerHeroId);
  const weaponSwitchState = advanceWeaponSwitchTimerState({
    deltaMs,
    weaponCount: player?.weapons.length ?? 0,
    weaponSwitchRemainingMs: context.weaponSwitchRemainingMs,
    weaponSwitchTotalMs: context.weaponSwitchTotalMs,
    pendingWeaponIndex: context.pendingWeaponIndex
  });
  if (player && weaponSwitchState.completedWeaponIndex !== null) {
    player.currentWeaponIndex = weaponSwitchState.completedWeaponIndex;
  }

  return {
    weaponSwitchRemainingMs: weaponSwitchState.weaponSwitchRemainingMs,
    weaponSwitchTotalMs: weaponSwitchState.weaponSwitchTotalMs,
    pendingWeaponIndex: weaponSwitchState.pendingWeaponIndex
  };
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
