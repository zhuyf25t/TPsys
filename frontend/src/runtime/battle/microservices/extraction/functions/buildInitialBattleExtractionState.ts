import type {
  BattleExtractionState,
  BattleExtractionZoneDefinition,
  BattleGasPlanDefinition,
  BattleGasZoneState,
  BattleLootCacheDefinition,
  BattleLootCacheState
} from "../../../../../objects/battle/microservices/extraction/objects/extraction/BattleExtractionDefinitions";

export interface BuildInitialBattleExtractionStateInput {
  gasPlan: Readonly<BattleGasPlanDefinition> | null;
  extractionZones: ReadonlyArray<Readonly<BattleExtractionZoneDefinition>>;
  lootCaches: ReadonlyArray<Readonly<BattleLootCacheDefinition>>;
}

export interface InitialBattleExtractionState {
  gasZone: BattleGasZoneState | null;
  extraction: BattleExtractionState | null;
  lootCaches: BattleLootCacheState[];
}

export function buildInitialBattleExtractionState({
  gasPlan,
  extractionZones,
  lootCaches
}: BuildInitialBattleExtractionStateInput): InitialBattleExtractionState {
  return {
    gasZone: buildInitialBattleGasZoneState(gasPlan),
    extraction: extractionZones.length > 0
      ? {
          zones: extractionZones.map((zone) => ({ ...zone, position: { ...zone.position } })),
          status: { status: "inactive" }
        }
      : null,
    lootCaches: lootCaches.map((cache) => ({
      ...cache,
      position: { ...cache.position },
      status: { status: "available" }
    }))
  };
}

function buildInitialBattleGasZoneState(
  gasPlan: Readonly<BattleGasPlanDefinition> | null
): BattleGasZoneState | null {
  const firstStage = gasPlan?.stages[0];
  if (!gasPlan || !firstStage) {
    return null;
  }

  return {
    phase: "waiting",
    center: { ...gasPlan.center },
    radius: firstStage.fromRadius,
    nextRadius: firstStage.toRadius,
    damagePerSecond: 0,
    stageIndex: 0,
    progressMs: 0,
    startsAtMs: firstStage.startsAtMs,
    endsAtMs: firstStage.startsAtMs + firstStage.durationMs
  };
}
