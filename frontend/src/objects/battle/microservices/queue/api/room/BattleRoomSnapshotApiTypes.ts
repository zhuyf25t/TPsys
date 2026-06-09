import type {
  BattleModeIdDto,
  BattleQueueParticipantResponseDto,
  BattleRoomChatMessageResponseDto,
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
  startsAt: number;
  deadline: number;
  serverTime: number;
  participants: BattleQueueParticipantResponseDto[];
  capacity: number;
  durationMs: number;
  phase: MatchmakingRoomPhaseDto;
  startPaused: boolean;
  pausedRemainingMs?: number;
  chatMessages: BattleRoomChatMessageResponseDto[];
  finishedAt?: number;
  battleSession?: BattleSessionDescriptorResponseDto;
}
