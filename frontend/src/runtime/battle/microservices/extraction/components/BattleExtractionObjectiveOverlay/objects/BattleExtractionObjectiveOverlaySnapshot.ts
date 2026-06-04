import type {
  BattleExtractionState,
  BattleGasZoneState,
  BattleLootCacheState
} from "../../../../../../../objects/battle/microservices/extraction/objects/extraction/BattleExtractionDefinitions";
import type { BattleVector2 } from "../../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattleExtractionObjectiveOverlaySnapshot {
  gasZone: BattleGasZoneState | null;
  extraction: BattleExtractionState | null;
  lootCaches: readonly BattleLootCacheState[];
  worldSize?: BattleVector2;
}
