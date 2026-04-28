import type { GameSnapshot, Hero } from "../../../../domain/types";

export function getPlayerHeroFromSnapshot(snapshot: GameSnapshot): Hero {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  if (!player) { throw new Error("Player hero not found"); }
  return player;
}
