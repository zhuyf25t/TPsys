import type { GameSnapshot, Hero } from "../../../objects/types";
import { BATTLE_MATCH_DURATION_MS } from "../state/battleLocalGateway";

export function getBattleAliveHeroCount(snapshot: GameSnapshot): number {
  return snapshot.heroes.filter(isHeroBattleAlive).length;
}

export function isBattleComplete(snapshot: GameSnapshot | null): snapshot is GameSnapshot {
  if (!snapshot) {
    return false;
  }

  return getBattleAliveHeroCount(snapshot) <= 1 || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS;
}

export function isLocalPlayerEliminated(snapshot: GameSnapshot | null): snapshot is GameSnapshot {
  if (!snapshot) {
    return false;
  }

  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  return Boolean(player && !isHeroBattleAlive(player));
}

function isHeroBattleAlive(hero: Hero): boolean {
  return hero.alive && hero.lifeState === "alive" && hero.hp > 0;
}
