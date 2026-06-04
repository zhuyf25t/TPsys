import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";

interface BattleNonLocalHeroStaminaRecoveryInput {
  hero: Hero;
  playerHeroId: string;
  deltaMs: number;
  staminaRecoverPerSecond: number;
}

interface BattleDeadHeroRuntimeState {
  velocity: Hero["velocity"];
  preparedSkill: null;
}

const NON_LOCAL_STAMINA_RECOVERY_FACTOR = 0.5;

export function advanceBattleHeroJumpCooldownMs(jumpCooldownMs: number, deltaMs: number): number {
  return Math.max(0, jumpCooldownMs - Math.max(0, deltaMs));
}

export function resolveBattleDeadHeroRuntimeState(hero: Hero): BattleDeadHeroRuntimeState | null {
  if (hero.alive) {
    return null;
  }

  return {
    velocity: { x: 0, y: 0 },
    preparedSkill: null
  };
}

export function recoverBattleNonLocalHeroStamina({
  hero,
  playerHeroId,
  deltaMs,
  staminaRecoverPerSecond
}: BattleNonLocalHeroStaminaRecoveryInput): number {
  if (!hero.alive || hero.heroId === playerHeroId) {
    return hero.stamina;
  }

  const deltaSeconds = Math.max(0, deltaMs) / 1000;
  return Math.min(
    hero.maxStamina,
    hero.stamina + staminaRecoverPerSecond * deltaSeconds * NON_LOCAL_STAMINA_RECOVERY_FACTOR
  );
}
