import type { BattleCommandSkillIntents } from "../../../abilities/objects/skill/BattleCommandSkillIntents";
import type { SkillKind } from "../../../abilities/objects/skill/SkillKind";
import type { SkillOutcomeReason } from "../../../abilities/objects/skill/SkillOutcomeReason";
import type { SkillOutcomeStatus } from "../../../abilities/objects/skill/SkillOutcomeStatus";
import type { BattleWeaponSwitchDirection } from "../../../combat/objects/weapon/BattleWeaponSwitchDirection";
import type { BattleWeaponSwitchIndex } from "../../../combat/objects/weapon/BattleWeaponSwitchIndex";
import type {
  BattleId,
  BattleTick,
  ClientCommandSeq,
  EpochMillis,
  PlayerId,
  TicketId
} from "../../../../objects/core/BattleCoreScalars";
import type { BattleCommandReason } from "./BattleCommandReason";
import type { BattleCommandStatus } from "./BattleCommandStatus";

export interface BattleCommandVector {
  x: number;
  y: number;
}

export interface BattleCommandRequest {
  battleId: BattleId;
  playerId: PlayerId;
  ticketId: TicketId;
  clientTick: BattleTick;
  clientCommandSeq: ClientCommandSeq;
  movement: BattleCommandVector;
  aim: BattleCommandVector;
  primaryHeld: boolean;
  sprint: boolean;
  reloadPressed: boolean;
  skillIntents: BattleCommandSkillIntents;
  pointerWorld: BattleCommandVector | null;
  switchWeaponDirection: BattleWeaponSwitchDirection;
  switchWeaponIndex: BattleWeaponSwitchIndex | null;
}

export interface BattleCommandSkillOutcome {
  action: SkillKind;
  outcomeStatus: SkillOutcomeStatus;
  reason: SkillOutcomeReason | null;
}

export interface BattleCommandAccepted {
  battleId: BattleId;
  acceptedTick: BattleTick;
  acceptedCommandSeq: ClientCommandSeq;
  serverTime: EpochMillis;
  commandStatus: BattleCommandStatus;
  commandReason: BattleCommandReason | null;
  outcomes: BattleCommandSkillOutcome[];
}

