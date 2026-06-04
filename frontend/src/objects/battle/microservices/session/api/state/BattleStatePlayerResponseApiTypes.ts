import type { SkillKind } from "../../../abilities/objects/skill/SkillKind";
import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type { BattleApiVectorDto } from "./BattleStateSharedResponseApiTypes";

export interface BattleStateWeaponResponseDto {
  weaponKind: WeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
}

export interface BattleStateSkillResponseDto {
  kind: SkillKind;
  cooldownMs: number;
  activeMs: number;
}

export interface BattleStatePlayerResponseDto {
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  seat: number;
  isBot: boolean;
  position: BattleApiVectorDto;
  aim: BattleApiVectorDto;
  facing: number;
  movement: BattleApiVectorDto;
  sprint: boolean;
  primaryHeld: boolean;
  reloadPressed: boolean;
  lastClientCommandSeq: number;
  currentWeaponIndex: number;
  weapons: BattleStateWeaponResponseDto[];
  currentWeaponKind: WeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  score: number;
  kills: number;
  skills: BattleStateSkillResponseDto[];
  alive: boolean;
  eliminatedAtMs: number | null;
  respawnMs: number;
}

