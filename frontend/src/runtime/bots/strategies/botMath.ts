import type { BattleVector2 as Vec2 } from "../../../objects/battle/objects/core/BattleCoreScalars";

/** 中文名：英雄seed（heroSeed）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function heroSeed(heroId: string): number {
  let hash = 0;
  for (let index = 0; index < heroId.length; index += 1) {
    hash = (hash * 31 + heroId.charCodeAt(index)) % 997;
  }

  return hash / 997;
}

/** 中文名：distancebetween（distanceBetween）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
