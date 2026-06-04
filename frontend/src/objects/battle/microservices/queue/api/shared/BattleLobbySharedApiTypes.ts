import type { BattleModeId } from "../../../../objects/core/BattleCoreScalars";
import type { MatchmakingRoomPhase } from "../../objects/queue/MatchmakingRoomPhase";

export type BattleModeIdDto = BattleModeId;
export type MatchmakingRoomPhaseDto = MatchmakingRoomPhase;

export interface BattleQueueParticipantResponseDto {
  playerId: string;
  handle: string;
  joinedAt: number;
  lastSeen: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface BattleSessionRosterEntryResponseDto {
  seat: number;
  playerId: string;
  handle: string;
  joinedAt: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface BattleSessionBootstrapSeatResponseDto {
  seat: number;
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  joinedAt: number;
  isBot: boolean;
  spawnPointIndex: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface BattleSessionBootstrapResponseDto {
  seats: BattleSessionBootstrapSeatResponseDto[];
}

export interface BattleSessionDescriptorResponseDto {
  battleId: string;
  modeId: BattleModeIdDto;
  modeLabel: string;
  mapId: string;
  mapLabel: string;
  startedAt: number;
  serverTime: number;
  roster: BattleSessionRosterEntryResponseDto[];
  capacity: number;
  bootstrap?: BattleSessionBootstrapResponseDto;
}

