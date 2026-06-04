import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";

export type SlowFieldId = string;

export interface BattleSlowFieldState {
  fieldId: SlowFieldId;
  ownerPlayerId?: string;
  ownerHeroId: string;
  position: BattleVector2;
  radius: number;
  ttlMs: number;
  durationMs: number;
}
