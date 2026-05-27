import type { GameSnapshot, Hero } from "../../../../objects/battle/types";
import { BATTLE_MATCH_DURATION_MS } from "../state/battleLocalGateway";

/** 中文名：获取战斗alive英雄count（getBattleAliveHeroCount）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getBattleAliveHeroCount(snapshot: GameSnapshot): number {
  return snapshot.heroes.filter(isHeroBattleAlive).length;
}

/** 中文名：判断是否战斗complete（isBattleComplete）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isBattleComplete(snapshot: GameSnapshot | null): snapshot is GameSnapshot {
  if (!snapshot) {
    return false;
  }

  return getBattleAliveHeroCount(snapshot) <= 1 || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS;
}

/** 中文名：判断是否本地玩家eliminated（isLocalPlayerEliminated）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
