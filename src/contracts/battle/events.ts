import type { HeroId } from "./commands";

export type BattleEventKindDto = "kill" | "heal" | "pickup" | "respawn" | "jump" | "switch" | "system";

export interface BattleEventDto {
  eventId: string;
  kind: BattleEventKindDto;
  message: string;
  createdAtMs: number;
  sourceHeroId?: HeroId;
  targetHeroId?: HeroId;
}
