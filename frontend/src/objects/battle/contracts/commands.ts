import type {
  BattleApiVectorDto,
  BattleCommandAPIMessageRequest,
  BattleSkillKindDto
} from "./apiMessages";

export type LocalBattleSessionId = string;
export type LocalBattleReplayId = string;
export type LocalHeroId = string;
export type LocalProjectileId = string;
export type LocalPickupId = string;
export type LocalBattleTick = number;

export type LocalBattlePhaseDto = "queue" | "loading" | "active" | "finished" | "disconnected";

export type Vec2Dto = BattleApiVectorDto;

export type BattleCommandDto = BattleCommandAPIMessageRequest;

export type LocalBattleTeamDto = "FreeForAll";
export type LocalBattleLifeStateDto = "alive" | "dead" | "respawning";
export type LocalBattlePickupKindDto = "weapon" | "medkit";
export type LocalBattlePreparedSkillDto = Extract<BattleSkillKindDto, "Blink" | "Freeze"> | null;

export interface LocalBattleCommandDto {
  sessionId: LocalBattleSessionId;
  playerId: LocalHeroId;
  tick: LocalBattleTick;
  movement: Vec2Dto;
  aim: Vec2Dto;
  pointerWorld: Vec2Dto;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex?: number | null;
  preparedSkill: LocalBattlePreparedSkillDto;
  castBlink: boolean;
  castDash: boolean;
  reloadPressed: boolean;
}
