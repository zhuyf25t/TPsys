import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";

export type MatchPhase = "matching" | "playing" | "settled";
export type BattleDrawerId = "replay" | "discussion" | "rating" | "mails" | "social";

export interface ActiveBattleSessionOwner {
  handle: string;
  sessionToken: string | null;
}

export interface ActiveBattleSession {
  version: 1;
  owner: ActiveBattleSessionOwner;
  sessionEpoch?: string;
  battleId: string;
  mapId?: string;
  sharedAuthoritativeRuntime?: boolean;
  localAuthoritativePlayerId?: string;
  localAuthoritativeTicketId?: string;
  savedAt: number;
  snapshot: GameSnapshot;
  replayFrames: ReplayFrame[];
  lastReplaySampleElapsed: number | null;
}

export interface BattlePageTransientNotice {
  id: number;
  message: string;
}
