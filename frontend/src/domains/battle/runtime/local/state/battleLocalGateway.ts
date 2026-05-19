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
import type { GameSnapshot } from "../../../objects/types";

export {
  BATTLE_MATCH_DURATION_MS,
  type FinalizeBattleOutput,
  type LocalBattleLiveSummary,
  type LocalBattleReturnSummary
};

export function getLatestBattleResultSummary(): LocalBattleReturnSummary | null {
  return getLatestBattleReturnSummary();
}

export function summarizeLiveBattle(snapshot: GameSnapshot | null): LocalBattleLiveSummary | null {
  if (!snapshot) {
    return null;
  }

  return buildLiveBattleSummary(snapshot);
}

export function finalizeLocalBattle(input: FinalizeBattleInput): FinalizeBattleOutput | null {
  return finalizeBattleAndPersist(input);
}
