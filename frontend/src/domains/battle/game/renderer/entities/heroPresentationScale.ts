import { HERO_SPRITE_SCALE } from "../../constants";

const LOCAL_PLAYER_HERO_SCALE = 1.46;

export function getHeroBasePresentationScale(heroId: string, playerHeroId: string): number {
  return heroId === playerHeroId ? LOCAL_PLAYER_HERO_SCALE : HERO_SPRITE_SCALE;
}
