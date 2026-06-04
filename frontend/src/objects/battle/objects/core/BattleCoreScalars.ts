export interface BattleVector2 {
  x: number;
  y: number;
}

export type BattleId = string;
export type RoomId = string;
export type PlayerId = string;
export type HeroId = string;
export type TicketId = string;
export type BattleTick = number;
export type ClientCommandSeq = number;
export type EpochMillis = number;
export type ElapsedMillis = number;
export type DurationMillis = number;
export type CooldownMillis = number;
export type FacingRadians = number;
export type SeatIndex = number;
export type BattleTeamMode = "FreeForAll";
export type BattleModeId = "default" | "autumn" | "winter" | "normal";
export type BattlePhase = "waiting" | "active" | "finished";
export type BattleModeLabel = string;
export type BattleMapLabel = string;
