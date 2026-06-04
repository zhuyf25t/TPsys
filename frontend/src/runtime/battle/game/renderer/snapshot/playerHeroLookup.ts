import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";

/** 中文名：获取玩家英雄从快照（getPlayerHeroFromSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getPlayerHeroFromSnapshot(snapshot: GameSnapshot): Hero {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  if (!player) { throw new Error("Player hero not found"); }
  return player;
}
