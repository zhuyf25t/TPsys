import type {
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
} from "../../../../objects/core/BattleCoreScalars";
import type { BattleSurvivalOutcome } from "../../../actors/objects/player/BattleSurvivalOutcome";
import type { Rating } from "../../../actors/objects/player/BattlePlayerRating";
import type { Score } from "../../../actors/objects/player/BattlePlayerStats";
import type {
  BattleHighlightLine,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultLabel,
  BattleTimelineHint,
  RatingDelta
} from "./BattleResultPresentationValues";

export interface BattleResultRecordCommand {
  battleId: BattleId;
  handle: string;
  displayName: string;
  finishedAt: EpochMillis;
  finishedAtLabel: string;
  durationMs: DurationMillis;
  score: Score;
  placement: BattlePlacement | null;
  survivalOutcome: BattleSurvivalOutcome;
  ratingBefore: Rating;
  ratingDelta: RatingDelta;
  ratingAfter: Rating;
  resultLabel: BattleResultLabel;
  modeLabel: BattleModeLabel;
  mapLabel: BattleMapLabel;
  highlightLine: BattleHighlightLine;
  playersLine: BattlePlayersLine;
  timelineHint: BattleTimelineHint;
  currentLoadout: string | null;
}

