import type {
  BattleStatePickupResponseDto,
  BattleStatePlayerResponseDto,
  BattleStateProjectileResponseDto,
  BattleStateResponseDto,
  BattleStateSkillResponseDto,
  BattleStateWeaponResponseDto,
  BattlePickupKindDto as BackendBattlePickupKindDto,
  BattleProjectileKindDto,
  BattleSkillKindDto,
  BattleWeaponKindDto
} from "./apiMessages";
import type {
  LocalBattlePhaseDto,
  LocalBattlePickupKindDto,
  LocalBattleTeamDto,
  LocalBattleLifeStateDto,
  LocalBattleSessionId,
  LocalHeroId,
  LocalPickupId,
  LocalProjectileId,
  Vec2Dto
} from "./commands";
import type { LocalBattleEventDto } from "./events";

export type BattleSnapshotDto = BattleStateResponseDto;
export type BattleHeroViewDto = BattleStatePlayerResponseDto;
export type BattleProjectileViewDto = BattleStateProjectileResponseDto;
export type BattlePickupViewDto = BattleStatePickupResponseDto;
export type BattleWeaponViewDto = BattleStateWeaponResponseDto;
export type BattleSkillViewDto = BattleStateSkillResponseDto;
export type BattleBackendPickupKindDto = BackendBattlePickupKindDto;

export interface LocalBattleWorldObstacleDto {
  obstacleId: string;
  position: Vec2Dto;
  size: Vec2Dto;
  occludable: boolean;
}

export interface LocalBattleWorldViewDto {
  width: number;
  height: number;
  obstacles: LocalBattleWorldObstacleDto[];
}

export interface LocalBattleWeaponViewDto {
  weaponKind: BattleWeaponKindDto;
  ammoInMagazine: number | null;
  reserveAmmo: number | null;
  heat: number | null;
  overheated: boolean;
  cooldownRemaining: number;
  reloadRemaining: number;
}

export interface LocalBattleSkillViewDto {
  kind: BattleSkillKindDto;
  cooldownMs: number;
  activeMs: number;
}

export interface LocalBattleHeroViewDto {
  heroId: LocalHeroId;
  displayName: string;
  team: LocalBattleTeamDto;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  position: Vec2Dto;
  facing: number;
  radius: number;
  lifeState: LocalBattleLifeStateDto;
  score: number;
  currentWeaponIndex: number;
  weapons: LocalBattleWeaponViewDto[];
  skills: LocalBattleSkillViewDto[];
  preparedSkill: "Blink" | "Freeze" | null;
  velocity: Vec2Dto;
  respawnMs: number;
  jumpCooldownMs: number;
}

export interface LocalBattleProjectileViewDto {
  projectileId: LocalProjectileId;
  kind: BattleProjectileKindDto;
  ownerHeroId: LocalHeroId;
  position: Vec2Dto;
  velocity: Vec2Dto;
  facing: number;
  radius: number;
  damage: number;
  splashRadius: number;
}

export interface LocalBattlePickupViewDto {
  pickupId: LocalPickupId;
  kind: LocalBattlePickupKindDto;
  label: string;
  position: Vec2Dto;
  available: boolean;
}

export interface LocalBattleSnapshotDto {
  sessionId: LocalBattleSessionId;
  phase: LocalBattlePhaseDto;
  elapsedMs: number;
  world: LocalBattleWorldViewDto;
  heroes: LocalBattleHeroViewDto[];
  projectiles: LocalBattleProjectileViewDto[];
  pickups: LocalBattlePickupViewDto[];
  events: LocalBattleEventDto[];
  localPlayerHeroId: LocalHeroId;
}
