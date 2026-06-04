import type {
  BattleModeIdDto,
  BattleQueueParticipantResponseDto,
  BattleSessionDescriptorResponseDto,
  MatchmakingRoomPhaseDto
} from "../shared/BattleLobbySharedApiTypes";

export interface BattleRoomSnapshotAPIMessageRequest {
  userToken?: string;
  roomId: string;
}

export interface RealtimeRoomSnapshotResponseDto {
  roomId: string;
  modeId: BattleModeIdDto;
  modeLabel: string;
  mapId: string;
  mapLabel: string;
  serverTime: number;
  participants: BattleQueueParticipantResponseDto[];
  capacity: number;
  phase: MatchmakingRoomPhaseDto;
  finishedAt?: number;
  battleSession?: BattleSessionDescriptorResponseDto;
}

