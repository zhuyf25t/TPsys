import type {
  BattleExtractionState,
  BattleGasZoneState,
  BattleLootCacheState
} from "../../../../../objects/battle/microservices/extraction/objects/extraction/BattleExtractionDefinitions";

export interface BattleExtractionSnapshotFields {
  gasZone: BattleGasZoneState | null;
  extraction: BattleExtractionState | null;
  lootCaches: BattleLootCacheState[];
}

export function cloneBattleExtractionSnapshotFields(
  fields: BattleExtractionSnapshotFields
): BattleExtractionSnapshotFields {
  return {
    gasZone: cloneBattleGasZoneState(fields.gasZone),
    extraction: cloneBattleExtractionState(fields.extraction),
    lootCaches: fields.lootCaches.map(cloneBattleLootCacheState)
  };
}

export function cloneBattleGasZoneState(state: BattleGasZoneState | null): BattleGasZoneState | null {
  return state
    ? {
        ...state,
        center: { ...state.center }
      }
    : null;
}

export function cloneBattleExtractionState(state: BattleExtractionState | null): BattleExtractionState | null {
  return state
    ? {
        zones: state.zones.map((zone) => ({ ...zone, position: { ...zone.position } })),
        status: { ...state.status }
      }
    : null;
}

export function cloneBattleLootCacheState(state: BattleLootCacheState): BattleLootCacheState {
  return {
    ...state,
    position: { ...state.position },
    status: { ...state.status }
  };
}
