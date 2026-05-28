import type { BattleEventKindDto, BattleStateEventResponseDto } from "./apiMessages";
import type { LocalHeroId } from "./commands";

export type BattleEventDto = BattleStateEventResponseDto;
export type LocalBattleEventKindDto = BattleEventKindDto | "jump" | "switch";

export interface LocalBattleEventDto {
  eventId: string;
  kind: LocalBattleEventKindDto;
  message: string;
  createdAtMs: number;
  sourceHeroId?: LocalHeroId;
  targetHeroId?: LocalHeroId;
}
