import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface PendingLocalAuthoritativeCorrection {
  targetPosition: Vec2;
}

export type LocalAuthoritativeHeroCorrectionUpdatePlan =
  | { kind: "clear-pending" }
  | { kind: "keep-pending" }
  | {
      kind: "write-position";
      nextPosition: Vec2;
      clearPending: boolean;
    };
