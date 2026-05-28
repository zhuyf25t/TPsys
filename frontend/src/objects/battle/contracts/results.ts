import type { BattleResultListResponseDto, BattleResultRecordResponseDto } from "./apiMessages";
import type { LocalBattleReplayId, LocalBattleSessionId, LocalHeroId } from "./commands";

export type BattleResultDto = BattleResultRecordResponseDto;
export type BattleResultsDto = BattleResultListResponseDto;

export type LocalBattleSessionResultOutcomeDto = "finished" | "abandoned";

export interface LocalBattleSessionResultDto {
  sessionId: LocalBattleSessionId;
  replayId: LocalBattleReplayId | null;
  outcome: LocalBattleSessionResultOutcomeDto;
  playerHeroId: LocalHeroId;
  score: number;
  kills: number;
  deaths: number;
  placement: number | null;
  ratingDelta: number | null;
  earnedMailIds: string[];
  finishedAtMs: number;
}
