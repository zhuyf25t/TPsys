export interface BattleQueueLeaveAPIMessageRequest {
  userToken?: string;
  ticketId: string;
}

export interface BattleQueueLeaveResponseDto {
  left: boolean;
}

