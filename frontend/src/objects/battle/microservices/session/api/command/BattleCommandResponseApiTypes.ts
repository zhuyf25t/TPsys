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

export type BattleCommandAcceptPathDto = "fresh" | "serialized";

export interface BattleCommandServerDiagnosticsResponseDto {
  path: BattleCommandAcceptPathDto;
  receivedAt: number;
  completedAt: number;
  durationMs: number;
  lockWaitMs: number;
  lockHeldMs: number;
  advanceMs: number;
  commitRetryCount: number;
  clientTick: number;
  acceptedTick: number;
  acceptedTickLag: number;
  clientCommandSeq: number;
  acceptedCommandSeq: number;
  acceptedCommandSeqLag: number;
}

export interface BattleCommandAcceptedResponseDto {
  battleId: string;
  acceptedTick: number;
  acceptedCommandSeq: number;
  serverTime: number;
  commandStatus: BattleCommandStatusDto;
  commandReason?: BattleCommandReasonDto;
  outcomes: BattleCommandSkillOutcomeResponseDto[];
  serverDiagnostics?: BattleCommandServerDiagnosticsResponseDto;
}

