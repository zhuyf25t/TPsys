import type {
  BattleExtractionInterruptReason,
  BattleExtractionState,
  BattleExtractionStatus,
  BattleExtractionZoneDefinition,
  BattleGasPhase,
  BattleGasZoneState,
  BattleLootCacheState,
  BattleLootCacheStatus
} from "../../objects/extraction/BattleExtractionDefinitions";

export type BattleGasPhaseDto = BattleGasPhase;
export type BattleExtractionStatusDto = BattleExtractionStatus["status"];
export type BattleExtractionInterruptReasonDto = BattleExtractionInterruptReason;
export type BattleLootCacheStatusDto = BattleLootCacheStatus["status"];

export type BattleGasZoneResponseDto = BattleGasZoneState;
export type BattleExtractionZoneResponseDto = BattleExtractionZoneDefinition;
export type BattleExtractionStatusResponseDto = BattleExtractionStatus;
export type BattleExtractionResponseDto = BattleExtractionState;
export type BattleLootCacheStatusResponseDto = BattleLootCacheStatus;
export type BattleLootCacheResponseDto = BattleLootCacheState;

