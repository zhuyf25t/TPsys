export type RatingDelta = number;
export type BattleResultLabel = string;
export type BattleHighlightLine = string;
export type BattlePlayersLine = string;
export type BattleTimelineHint = string;
export type BattlePlacement = number;

export function battlePlacementFromWire(value: number): BattlePlacement | null {
  return Number.isFinite(value) && value > 0 ? Math.trunc(value) : null;
}

