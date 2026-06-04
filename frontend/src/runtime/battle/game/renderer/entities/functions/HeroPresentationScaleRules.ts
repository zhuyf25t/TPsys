import { HERO_SPRITE_SCALE } from "../../../objects/BattleGameConstants";

const LOCAL_PLAYER_HERO_SCALE = 1.46;
const BOSS_ZOMBIE_HERO_SCALE = 1.88;

export function getHeroBasePresentationScale(heroId: string, playerHeroId: string): number {
  if (heroId !== playerHeroId && isBossZombieHeroId(heroId)) {
    return BOSS_ZOMBIE_HERO_SCALE;
  }

  return heroId === playerHeroId ? LOCAL_PLAYER_HERO_SCALE : HERO_SPRITE_SCALE;
}

function isBossZombieHeroId(heroId: string): boolean {
  return heroId === "bot-1" || heroId === "bot-2" || heroId === "bot-3";
}
