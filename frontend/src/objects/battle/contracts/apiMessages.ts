export interface BattleApiVectorDto {
  x: number;
  y: number;
}

export type BattleModeIdDto = "default" | "autumn" | "winter" | "normal";
export type MatchmakingRoomPhaseDto = "waiting" | "active" | "finished" | "unknown";
export type AuthoritativeBattlePhaseDto = "waiting" | "active" | "finished";
export type BattleWeaponKindDto = "Pistol" | "RocketLauncher" | "Gatling" | "Shotgun";
export type BattleProjectileKindDto = "pistol-bullet" | "rocket" | "gatling-bullet" | "shotgun-pellet";
export type BattleSkillKindDto = "Blink" | "Dash" | "Freeze";
export type BattlePickupKindDto = "Medkit" | "Weapon";
export type BattleCommandStatusDto = "applied" | "ignored";
export type BattleCommandReasonDto = "battle_finished" | "battle_inactive" | "player_dead";
export type BattleSkillOutcomeStatusDto = "applied" | "noop";
export type BattleSkillOutcomeReasonDto =
  | "skill_not_owned"
  | "cooldown"
  | "missing_target"
  | "out_of_range"
  | "invalid_target"
  | "no_direction"
  | "blocked";
export type BattleEventKindDto = "kill" | "heal" | "pickup" | "respawn";
export type BattleProjectileTerminalReasonDto = "hit" | "ttl" | "obstacle" | "world";

export interface BattleTokenizedAPIMessageRequestDto {
  userToken?: string;
}

export interface BattleQueueJoinAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  handle: string;
  sessionToken: string;
  modeId?: BattleModeIdDto;
  queueRequestId?: string;
  rating?: number | string;
  avatar?: string;
  skin?: string;
}

export interface BattleQueueStatusAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  ticketId: string;
}

export interface BattleQueueLeaveAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  ticketId: string;
}

export interface BattleRoomSnapshotAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  roomId: string;
}

export interface BattleRoomHeartbeatAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  roomId?: string;
  ticketId?: string;
  handle?: string;
}

export interface BattleStateReadAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  battleId: string;
}

export interface BattleCommandAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  battleId: string;
  playerId: string;
  ticketId: string;
  clientTick: number;
  clientCommandSeq?: number;
  movement: BattleApiVectorDto;
  aim: BattleApiVectorDto;
  primaryHeld: boolean;
  sprint?: boolean;
  reloadPressed: boolean;
  castDash?: boolean;
  castBlink?: boolean;
  castFreeze?: boolean;
  pointerWorld?: BattleApiVectorDto | null;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex?: number | null;
}

export interface BattleResultListAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  handle?: string;
  battleId?: string;
  limit?: number;
}

export interface BattleResultRecordAPIMessageRequest extends BattleTokenizedAPIMessageRequestDto {
  battleId: string;
  handle: string;
  displayName?: string;
  finishedAt?: number;
  finishedAtLabel?: string;
  durationMs?: number;
  score?: number;
  placement?: number | null;
  aliveAtEnd?: boolean;
  ratingBefore?: number;
  ratingDelta?: number;
  ratingAfter?: number;
  resultLabel?: string;
  modeLabel?: string;
  mapLabel?: string;
  highlightLine?: string;
  playersLine?: string;
  timelineHint?: string;
  currentLoadout?: string | null;
}

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
  finishedAt?: number;
  battleSession?: BattleSessionDescriptorResponseDto;
}

export interface BattleQueueLeaveResponseDto {
  left: boolean;
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

export interface BattleStateWeaponResponseDto {
  weaponKind: BattleWeaponKindDto;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
}

export interface BattleStateSkillResponseDto {
  kind: BattleSkillKindDto;
  cooldownMs: number;
  activeMs: number;
}

export interface BattleStatePlayerResponseDto {
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  seat: number;
  isBot: boolean;
  position: BattleApiVectorDto;
  aim: BattleApiVectorDto;
  facing: number;
  movement: BattleApiVectorDto;
  sprint: boolean;
  primaryHeld: boolean;
  reloadPressed: boolean;
  lastClientCommandSeq: number;
  currentWeaponIndex: number;
  weapons: BattleStateWeaponResponseDto[];
  currentWeaponKind: BattleWeaponKindDto;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  score: number;
  kills: number;
  skills: BattleStateSkillResponseDto[];
  alive: boolean;
  eliminatedAtMs: number | null;
  respawnMs: number;
}

export interface BattleStateProjectileResponseDto {
  projectileId: string;
  ownerHeroId: string;
  kind: BattleProjectileKindDto;
  position: BattleApiVectorDto;
  velocity: BattleApiVectorDto;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
}

export interface BattleStateProjectileTerminalResponseDto {
  projectileId: string;
  kind: BattleProjectileKindDto;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: BattleProjectileTerminalReasonDto;
  start: BattleApiVectorDto;
  end: BattleApiVectorDto;
  terminalPosition: BattleApiVectorDto;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

export interface BattleStateSlowFieldResponseDto {
  fieldId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  position: BattleApiVectorDto;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export interface BattleStatePickupResponseDto {
  pickupId: string;
  kind: BattlePickupKindDto;
  position: BattleApiVectorDto;
  available: boolean;
  respawnMs: number;
  weaponKind?: BattleWeaponKindDto;
}

export interface BattleStateEventParticipantResponseDto {
  playerId: string;
  heroId: string;
  displayName: string;
}

export interface BattleStateEventResponseDto {
  eventId: string;
  type: BattleEventKindDto;
  kind: BattleEventKindDto;
  elapsedMs: number;
  message: string;
  source: BattleStateEventParticipantResponseDto;
  target: BattleStateEventParticipantResponseDto;
}

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
  events: BattleStateEventResponseDto[];
  winnerPlayerId?: string;
  winnerHeroId?: string;
}

export interface BattleCommandSkillOutcomeResponseDto {
  action: BattleSkillKindDto;
  status: BattleSkillOutcomeStatusDto;
  reason?: BattleSkillOutcomeReasonDto;
}

export interface BattleCommandAcceptedResponseDto {
  battleId: string;
  acceptedTick: number;
  acceptedCommandSeq: number;
  serverTime: number;
  commandStatus: BattleCommandStatusDto;
  commandReason?: BattleCommandReasonDto;
  outcomes: BattleCommandSkillOutcomeResponseDto[];
}

export interface BattleResultRecordResponseDto {
  resultId: string;
  battleId: string;
  handle: string;
  displayName: string;
  finishedAt: number;
  finishedAtLabel: string;
  durationMs: number;
  score: number;
  placement: number | null;
  aliveAtEnd: boolean;
  ratingBefore: number;
  ratingDelta: number;
  ratingAfter: number;
  resultLabel: string;
  modeLabel: string;
  mapLabel: string;
  highlightLine: string;
  playersLine: string;
  timelineHint: string;
  currentLoadout: string | null;
}

export interface BattleResultListResponseDto {
  results: BattleResultRecordResponseDto[];
}
