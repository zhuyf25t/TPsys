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

