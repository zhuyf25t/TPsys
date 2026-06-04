import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";

const CRITICAL_DAMAGE_MULTIPLIER = 1.5;

export function resolveBattleCriticalDamage(baseDamage: number, owner: Hero | null | undefined): number {
  if (!owner?.skills.some((skill) => skill.kind === "Critical" && skill.activeMs > 0)) {
    return baseDamage;
  }

  return Math.ceil(baseDamage * CRITICAL_DAMAGE_MULTIPLIER);
}
