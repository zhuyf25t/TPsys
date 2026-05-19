import { HERO_SPRITE_SCALE } from "../../constants";

const LOCAL_PLAYER_HERO_SCALE = 1.46;

/** 中文名：获取英雄basepresentationscale（getHeroBasePresentationScale）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getHeroBasePresentationScale(heroId: string, playerHeroId: string): number {
  return heroId === playerHeroId ? LOCAL_PLAYER_HERO_SCALE : HERO_SPRITE_SCALE;
}
