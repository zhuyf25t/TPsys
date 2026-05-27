import {
  BATTLE_MATCH_DURATION_MS,
  buildLiveBattleSummary,
  finalizeBattleAndPersist,
  getLatestBattleReturnSummary,
  type FinalizeBattleInput,
  type FinalizeBattleOutput,
  type LocalBattleLiveSummary,
  type LocalBattleReturnSummary
} from "./battleTruthStore";
import type { GameSnapshot } from "../../../../objects/battle/types";

export {
  BATTLE_MATCH_DURATION_MS,
  type FinalizeBattleOutput,
  type LocalBattleLiveSummary,
  type LocalBattleReturnSummary
};

/** 中文名：获取latest战斗结果摘要（getLatestBattleResultSummary）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getLatestBattleResultSummary(): LocalBattleReturnSummary | null {
  return getLatestBattleReturnSummary();
}

/** 中文名：summarizelive战斗（summarizeLiveBattle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function summarizeLiveBattle(snapshot: GameSnapshot | null): LocalBattleLiveSummary | null {
  if (!snapshot) {
    return null;
  }

  return buildLiveBattleSummary(snapshot);
}

/** 中文名：finalize本地战斗（finalizeLocalBattle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function finalizeLocalBattle(input: FinalizeBattleInput): FinalizeBattleOutput | null {
  return finalizeBattleAndPersist(input);
}
