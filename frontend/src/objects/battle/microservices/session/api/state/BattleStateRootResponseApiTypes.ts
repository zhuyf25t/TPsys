import type { BattleExtractionResponseDto, BattleGasZoneResponseDto, BattleLootCacheResponseDto } from "../../../extraction/api/state/BattleExtractionStateResponseApiTypes";
import type { BattlePhase } from "../../../../objects/core/BattleCoreScalars";
import type { BattleStateEventResponseDto, BattleStatePickupResponseDto, BattleStateSlowFieldResponseDto } from "./BattleStateEntityResponseApiTypes";
import type { BattleStatePlayerResponseDto } from "./BattleStatePlayerResponseApiTypes";
import type { BattleStateProjectileResponseDto, BattleStateProjectileTerminalResponseDto } from "./BattleStateProjectileResponseApiTypes";
import type { BattleApiVectorDto } from "./BattleStateSharedResponseApiTypes";

export type AuthoritativeBattlePhaseDto = BattlePhase;

export interface BattleStateResponseDto {
  battleId: string;
  roomId: string;
  mapId: string;
  phase: AuthoritativeBattlePhaseDto;
  serverTime: number;
  startedAt: number;
  durationMs: number;
  elapsedMs: number;
  endsAt: number;
  worldSize: BattleApiVectorDto;
  tick: number;
  resultReady: boolean;
  replayReady: boolean;
  players: BattleStatePlayerResponseDto[];
  projectiles: BattleStateProjectileResponseDto[];
  projectileTerminals: BattleStateProjectileTerminalResponseDto[];
  slowFields: BattleStateSlowFieldResponseDto[];
  pickups: BattleStatePickupResponseDto[];
  gasZone?: BattleGasZoneResponseDto | null;
  extraction?: BattleExtractionResponseDto | null;
  lootCaches?: BattleLootCacheResponseDto[];
  events: BattleStateEventResponseDto[];
  winnerPlayerId?: string;
  winnerHeroId?: string;
}

