import type { BattleId } from "../../../../objects/core/BattleCoreScalars";
import type { BattleResultRecord } from "./BattleResultRecord";
import type { BattleResultListLimit } from "./BattleResultIds";

export interface BattleResultList {
  results: BattleResultRecord[];
}

export interface BattleResultListQuery {
  handle: string | null;
  battleId: BattleId | null;
  limit: BattleResultListLimit;
}

