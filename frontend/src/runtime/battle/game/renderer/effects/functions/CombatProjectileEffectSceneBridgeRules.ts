import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { ResolveCombatProjectileEffectKnockbackTargetInput } from "../objects/CombatProjectileEffectSceneBridgeObjects";

export function resolveCombatProjectileEffectKnockbackTarget({
  heroes,
  heroId
}: ResolveCombatProjectileEffectKnockbackTargetInput): Hero | null {
  return heroes.find((hero) => hero.heroId === heroId && hero.alive) ?? null;
}
