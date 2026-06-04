export interface BattleVector2 {
  x: number;
  y: number;
}

export type BattleExtractionZoneId = string;
export type BattleLootCacheId = string;
export type BattleGasStageIndex = number;
export type BattleGasDamagePerSecond = number;
export type BattleExtractionProgressMillis = number;
export type BattleLootSearchProgressMillis = number;
export type BattleLootScoreValue = number;

export type BattleGasPhase = "waiting" | "advancing" | "final";

export interface BattleGasStageDefinition {
  startsAtMs: number;
  durationMs: number;
  fromRadius: number;
  toRadius: number;
  damagePerSecond: BattleGasDamagePerSecond;
}

export interface BattleGasPlanDefinition {
  center: BattleVector2;
  stages: readonly BattleGasStageDefinition[];
}

export interface BattleGasZoneState {
  phase: BattleGasPhase;
  center: BattleVector2;
  radius: number;
  nextRadius: number;
  damagePerSecond: BattleGasDamagePerSecond;
  stageIndex: BattleGasStageIndex;
  progressMs: number;
  startsAtMs: number;
  endsAtMs: number;
}

export interface BattleExtractionZoneDefinition {
  zoneId: BattleExtractionZoneId;
  position: BattleVector2;
  radius: number;
  availableFromMs: number;
  channelDurationMs: number;
}

export type BattleExtractionInterruptReason = "left_zone" | "eliminated";

export type BattleExtractionStatus =
  | { status: "inactive" }
  | { status: "available" }
  | {
      status: "extracting";
      playerId: string;
      heroId: string;
      zoneId: BattleExtractionZoneId;
      progressMs: BattleExtractionProgressMillis;
    }
  | {
      status: "extracted";
      playerId: string;
      heroId: string;
      zoneId: BattleExtractionZoneId;
      atElapsedMs: number;
    }
  | {
      status: "interrupted";
      playerId: string;
      heroId: string;
      zoneId: BattleExtractionZoneId;
      reason: BattleExtractionInterruptReason;
      atElapsedMs: number;
    };

export interface BattleExtractionState {
  zones: BattleExtractionZoneDefinition[];
  status: BattleExtractionStatus;
}

export interface BattleLootCacheDefinition {
  cacheId: BattleLootCacheId;
  position: BattleVector2;
  radius: number;
  searchDurationMs: number;
  scoreValue: BattleLootScoreValue;
}

export type BattleLootCacheStatus =
  | { status: "available" }
  | {
      status: "searching";
      playerId: string;
      heroId: string;
      progressMs: BattleLootSearchProgressMillis;
    }
  | {
      status: "searched";
      playerId: string;
      heroId: string;
      atElapsedMs: number;
    };

export interface BattleLootCacheState extends BattleLootCacheDefinition {
  status: BattleLootCacheStatus;
}

