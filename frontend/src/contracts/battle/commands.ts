export type BattleSessionId = string;
export type BattleReplayId = string;
export type HeroId = string;
export type ProjectileId = string;
export type PickupId = string;
export type BattleTick = number;

export type BattlePhaseDto = "queue" | "loading" | "active" | "finished" | "disconnected";

export interface Vec2Dto {
  x: number;
  y: number;
}

export type BattleTeamDto = "FreeForAll";
export type BattleLifeStateDto = "alive" | "dead" | "respawning";
export type BattleWeaponKindDto = "Pistol" | "RocketLauncher" | "Gatling" | "Shotgun";
export type BattleProjectileKindDto = "pistol-bullet" | "rocket" | "gatling-bullet" | "shotgun-pellet";
export type BattleSkillKindDto = "Blink" | "Dash" | "Freeze";
export type BattlePickupKindDto = "weapon" | "medkit";
export type BattlePreparedSkillDto = "Blink" | "Freeze" | null;

export interface BattleCommandDto {
  sessionId: BattleSessionId;
  playerId: HeroId;
  tick: BattleTick;
  movement: Vec2Dto;
  aim: Vec2Dto;
  pointerWorld: Vec2Dto;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex?: number | null;
  preparedSkill: BattlePreparedSkillDto;
  castBlink: boolean;
  castDash: boolean;
  reloadPressed: boolean;
}
