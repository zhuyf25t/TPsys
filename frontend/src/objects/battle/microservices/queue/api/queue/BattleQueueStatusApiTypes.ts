import type {
  BattleModeIdDto,
  BattleQueueParticipantResponseDto,
  BattleRoomChatMessageResponseDto,
  BattleSessionDescriptorResponseDto,
  MatchmakingRoomPhaseDto
} from "../shared/BattleLobbySharedApiTypes";

export interface BattleQueueStatusAPIMessageRequest {
  userToken?: string;
  ticketId: string;
}

export interface BattleQueueSnapshotResponseDto {
  ticketId: string;
  playerId: string;
  roomId: string;
  modeId: BattleModeIdDto;
  modeLabel: string;
  mapId: string;
  mapLabel: string;
  createdAt: number;
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
