export interface BattleInitialParticipantSeat {
  seat: number;
  playerId?: string;
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

export interface BattleInitialParticipantsConfig {
  localPlayerHandle: string;
  localPlayerId?: string;
  queuedHandles: string[];
  capacity: number;
  seats?: BattleInitialParticipantSeat[];
}

export interface BattleInitialHeroConfig {
  heroId: string;
  displayName?: string;
  skin?: string;
  spawnPointIndex?: number;
}
