export interface MatchmakingQueuePlayer {
  handle: string;
  joinedAt: number;
}

export interface MatchmakingQueueState {
  ticketId: string;
  matchId: string;
  startsAt: number;
  players: MatchmakingQueuePlayer[];
  capacity: number;
  durationMs: number;
}

