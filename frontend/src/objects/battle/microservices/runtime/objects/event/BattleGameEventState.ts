import type { BattleEventKind } from "./BattleEventKind";

export type BattleGameEventKind = BattleEventKind | "jump" | "switch";

export interface BattleGameEventState {
  eventId: string;
  type: BattleGameEventKind;
  message: string;
  ttlMs: number;
}

