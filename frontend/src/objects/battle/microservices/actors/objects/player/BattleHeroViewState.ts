import type { BattleStateSkillResponseDto } from "../../../session/api/state/BattleStatePlayerResponseApiTypes";
import type { BattleWeaponState } from "../../../combat/objects/weapon/BattleWeaponState";
import type { BattleTeamMode, BattleVector2 } from "../../../../objects/core/BattleCoreScalars";

export type BattleHeroLifeState = "alive" | "dead" | "respawning";
export type BattlePreparedSkill = "Blink" | "Freeze" | null;

export interface BattleHeroViewState {
  heroId: string;
  displayName: string;
  team: BattleTeamMode;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  position: BattleVector2;
  facing: number;
  radius: number;
  alive: boolean;
  lifeState: BattleHeroLifeState;
  score: number;
  currentWeaponIndex: number;
  weapons: BattleWeaponState[];
  skills: BattleStateSkillResponseDto[];
  preparedSkill: BattlePreparedSkill;
  velocity: BattleVector2;
  respawnMs: number;
  jumpCooldownMs: number;
  eliminatedAtMs: number | null;
}

