import type { BattleReplayId, BattleSessionId, HeroId } from "./commands";

export type BattleResultOutcomeDto = "finished" | "abandoned";

export interface BattleResultDto {
  sessionId: BattleSessionId;
  replayId: BattleReplayId | null;
  outcome: BattleResultOutcomeDto;
  playerHeroId: HeroId;
  score: number;
  kills: number;
  deaths: number;
  placement: number | null;
  ratingDelta: number | null;
  earnedMailIds: string[];
  finishedAtMs: number;
}
