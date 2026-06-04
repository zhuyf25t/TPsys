import type { SkillKind } from "../../../abilities/objects/skill/SkillKind";
import type { SkillOutcomeReason } from "../../../abilities/objects/skill/SkillOutcomeReason";
import type { SkillOutcomeStatus } from "../../../abilities/objects/skill/SkillOutcomeStatus";
import type { BattleCommandReason } from "../../objects/command/BattleCommandReason";
import type { BattleCommandStatus } from "../../objects/command/BattleCommandStatus";

export type BattleCommandStatusDto = BattleCommandStatus;
export type BattleCommandReasonDto = BattleCommandReason;

export interface BattleCommandSkillOutcomeResponseDto {
  action: SkillKind;
  status: SkillOutcomeStatus;
  reason?: SkillOutcomeReason;
}

export interface BattleCommandAcceptedResponseDto {
  battleId: string;
  acceptedTick: number;
  acceptedCommandSeq: number;
  serverTime: number;
  commandStatus: BattleCommandStatusDto;
  commandReason?: BattleCommandReasonDto;
  outcomes: BattleCommandSkillOutcomeResponseDto[];
}

