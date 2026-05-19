import type {
  BattlePhaseDto,
  BattlePickupKindDto,
  BattleProjectileKindDto,
  BattleSkillKindDto,
  BattleTeamDto,
  BattleWeaponKindDto,
  BattleLifeStateDto,
  BattleSessionId,
  HeroId,
  PickupId,
  ProjectileId,
  Vec2Dto
} from "./commands";
import type { BattleEventDto } from "./events";

export interface BattleWorldObstacleDto {
  obstacleId: string;
  position: Vec2Dto;
  size: Vec2Dto;
  occludable: boolean;
}

export interface BattleWorldViewDto {
  width: number;
  height: number;
  obstacles: BattleWorldObstacleDto[];
}

export interface BattleWeaponViewDto {
  weaponKind: BattleWeaponKindDto;
  ammoInMagazine: number | null;
  reserveAmmo: number | null;
  heat: number | null;
  overheated: boolean;
  cooldownRemaining: number;
  reloadRemaining: number;
}

export interface BattleSkillViewDto {
  kind: BattleSkillKindDto;
  cooldownMs: number;
  activeMs: number;
}

export interface BattleHeroViewDto {
  heroId: HeroId;
  displayName: string;
  team: BattleTeamDto;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  position: Vec2Dto;
  facing: number;
  radius: number;
  lifeState: BattleLifeStateDto;
  score: number;
  currentWeaponIndex: number;
  weapons: BattleWeaponViewDto[];
  skills: BattleSkillViewDto[];
  preparedSkill: "Blink" | "Freeze" | null;
  velocity: Vec2Dto;
  respawnMs: number;
  jumpCooldownMs: number;
}

export interface BattleProjectileViewDto {
  projectileId: ProjectileId;
  kind: BattleProjectileKindDto;
  ownerHeroId: HeroId;
  position: Vec2Dto;
  velocity: Vec2Dto;
  facing: number;
  radius: number;
  damage: number;
  splashRadius: number;
}

export interface BattlePickupViewDto {
  pickupId: PickupId;
  kind: BattlePickupKindDto;
  label: string;
  position: Vec2Dto;
  available: boolean;
}

export interface BattleSnapshotDto {
  sessionId: BattleSessionId;
  phase: BattlePhaseDto;
  elapsedMs: number;
  world: BattleWorldViewDto;
  heroes: BattleHeroViewDto[];
  projectiles: BattleProjectileViewDto[];
  pickups: BattlePickupViewDto[];
  events: BattleEventDto[];
  localPlayerHeroId: HeroId;
}
