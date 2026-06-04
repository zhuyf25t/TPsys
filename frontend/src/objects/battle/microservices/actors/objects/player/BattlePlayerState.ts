import type { BattleCommandSkillIntents } from "../../../abilities/objects/skill/BattleCommandSkillIntents";
import type { SkillKind } from "../../../abilities/objects/skill/SkillKind";
import type { BattleWeaponState } from "../../../combat/objects/weapon/BattleWeaponState";
import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type {
  BattleVector2,
  ClientCommandSeq,
  FacingRadians,
  HeroId,
  PlayerId,
  SeatIndex
} from "../../../../objects/core/BattleCoreScalars";
import type { BattleParticipantKind } from "./BattleParticipantKind";
import type { BattlePlayerLifeState } from "./BattlePlayerLifeState";
import type { KillCount, Score } from "./BattlePlayerStats";
import type { HitPoints, Stamina } from "./BattlePlayerVitals";

export interface BattlePlayerSkillState {
  skillKind: SkillKind;
  cooldownMs: number;
  activeMs: number;
}

export interface BattlePlayerState {
  playerId: PlayerId;
  heroId: HeroId;
  handle: string;
  displayName: string;
  seat: SeatIndex;
  participantKind: BattleParticipantKind;
  position: BattleVector2;
  aim: BattleVector2;
  facing: FacingRadians;
  movement: BattleVector2;
  sprint: boolean;
  primaryHeld: boolean;
  reloadPressed: boolean;
  lastClientCommandSeq: ClientCommandSeq;
  currentWeaponIndex: number;
  weapons: BattleWeaponState[];
  currentWeaponKind: WeaponKind;
  hp: HitPoints;
  maxHp: HitPoints;
  stamina: Stamina;
  maxStamina: Stamina;
  score: Score;
  kills: KillCount;
  skills: BattlePlayerSkillState[];
  lifeState: BattlePlayerLifeState;
}

export interface BattlePlayerCommandIntentState {
  movement: BattleVector2;
  aim: BattleVector2;
  sprint: boolean;
  primaryHeld: boolean;
  reloadPressed: boolean;
  skillIntents: BattleCommandSkillIntents;
}

