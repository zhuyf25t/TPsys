import type {
  BattleId,
  BattleModeId,
  DurationMillis,
  EpochMillis,
  HeroId,
  PlayerId,
  RoomId,
  SeatIndex
} from "../../../../objects/core/BattleCoreScalars";
import type { BattleAvatarKey, BattleSkinKey } from "../../../actors/objects/player/BattleAppearanceKeys";
import type { BattleParticipantKind } from "../../../actors/objects/player/BattleParticipantKind";
import type { Rating } from "../../../actors/objects/player/BattlePlayerRating";
import type { BattleCapacity } from "./BattleCapacity";
import type { TicketId } from "./BattleQueueIds";
import type { MatchmakingRoomPhase } from "./MatchmakingRoomPhase";

export type SpawnPointIndex = number;

export interface BattleQueueParticipant {
  playerId: PlayerId;
  handle: string;
  joinedAt: EpochMillis;
  lastSeen: EpochMillis;
  rating: Rating | null;
  avatar: BattleAvatarKey | null;
  skin: BattleSkinKey | null;
}

export interface BattleSessionRosterEntry {
  seat: SeatIndex;
  playerId: PlayerId;
  handle: string;
  joinedAt: EpochMillis;
  rating: Rating | null;
  avatar: BattleAvatarKey | null;
  skin: BattleSkinKey | null;
}

export interface BattleSessionBootstrapSeat {
  seat: SeatIndex;
  playerId: PlayerId;
  heroId: HeroId;
  handle: string;
  displayName: string;
  joinedAt: EpochMillis;
  participantKind: BattleParticipantKind;
  spawnPointIndex: SpawnPointIndex;
  rating: Rating | null;
  avatar: BattleAvatarKey | null;
  skin: BattleSkinKey | null;
}

export interface BattleSessionBootstrap {
  seats: BattleSessionBootstrapSeat[];
}

export interface BattleSessionDescriptor {
  battleId: BattleId;
  battleMode: BattleModeId;
  startedAt: EpochMillis;
  serverTime: EpochMillis;
  roster: BattleSessionRosterEntry[];
  capacity: BattleCapacity;
  bootstrap: BattleSessionBootstrap | null;
}

export interface BattleQueueSnapshot {
  ticketId: TicketId;
  playerId: PlayerId;
  roomId: RoomId;
  battleMode: BattleModeId;
  createdAt: EpochMillis;
  startsAt: EpochMillis;
  deadline: EpochMillis;
  serverTime: EpochMillis;
  participants: BattleQueueParticipant[];
  capacity: BattleCapacity;
  durationMs: DurationMillis;
  phase: MatchmakingRoomPhase;
  finishedAt: EpochMillis | null;
  battleSession: BattleSessionDescriptor | null;
}

export interface RealtimeRoomSnapshot {
  roomId: RoomId;
  battleMode: BattleModeId;
  serverTime: EpochMillis;
  participants: BattleQueueParticipant[];
  capacity: BattleCapacity;
  phase: MatchmakingRoomPhase;
  finishedAt: EpochMillis | null;
  battleSession: BattleSessionDescriptor | null;
}

