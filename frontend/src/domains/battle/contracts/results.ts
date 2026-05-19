import type { BattleReplayId, BattleSessionId, HeroId } from "./commands";

export type BattleSessionResultOutcomeDto = "finished" | "abandoned";

// Local session result emitted by the frontend adapter; not the backend persisted battle result record.
export interface BattleSessionResultDto {
  sessionId: BattleSessionId;
  replayId: BattleReplayId | null;
  outcome: BattleSessionResultOutcomeDto;
  playerHeroId: HeroId;
  score: number;
  kills: number;
  deaths: number;
  placement: number | null;
  ratingDelta: number | null;
  earnedMailIds: string[];
  finishedAtMs: number;
}
