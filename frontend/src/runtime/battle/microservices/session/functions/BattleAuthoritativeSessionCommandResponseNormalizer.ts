import type {
  AuthoritativeBattleCommandAccepted,
  AuthoritativeBattleCommandReason,
  AuthoritativeBattleCommandStatus,
  BattleCommandAcceptPathDto,
  BattleCommandServerDiagnosticsResponseDto,
  AuthoritativeBattleSkillOutcome,
  AuthoritativeBattleSkillOutcomeReason,
  AuthoritativeBattleSkillOutcomeStatus
} from "../api/BattleAuthoritativeSessionClient";
import {
  normalizeAuthoritativeBattleSkillKind,
  normalizeRequiredArray,
  readNumber,
  readString
} from "./BattleAuthoritativeSessionNormalizerPrimitives";

type BattleCommandAPIMessagePayload =
  | { kind: "accepted"; accepted: AuthoritativeBattleCommandAccepted }
  | { kind: "error"; errorCode?: string };

function normalizeBattleCommandAccepted(payload: unknown): AuthoritativeBattleCommandAccepted | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleCommandAccepted> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const acceptedTick = readNumber(value.acceptedTick);
  const acceptedCommandSeq = readNumber(value.acceptedCommandSeq);
  const serverTime = readNumber(value.serverTime);
  const commandStatus = normalizeBattleCommandStatus(value.commandStatus);
  const hasCommandReason = Object.prototype.hasOwnProperty.call(value, "commandReason");
  const commandReason = normalizeBattleCommandReason(value.commandReason);
  const hasServerDiagnostics = Object.prototype.hasOwnProperty.call(value, "serverDiagnostics");
  const serverDiagnostics = normalizeBattleCommandServerDiagnostics(value.serverDiagnostics);
  const outcomesPayload = Array.isArray(value.outcomes) ? value.outcomes : null;
  const outcomes = outcomesPayload === null ? null : normalizeRequiredArray(outcomesPayload, normalizeBattleSkillOutcome);

  if (
    !battleId ||
    acceptedTick === null ||
    acceptedCommandSeq === null ||
    serverTime === null ||
    commandStatus === null ||
    outcomesPayload === null ||
    outcomes === null ||
    (hasCommandReason && commandReason === null) ||
    (hasServerDiagnostics && serverDiagnostics === null)
  ) {
    return null;
  }

  return {
    battleId,
    acceptedTick: Math.max(0, Math.trunc(acceptedTick)),
    acceptedCommandSeq: Math.max(0, Math.trunc(acceptedCommandSeq)),
    serverTime,
    commandStatus,
    ...(commandReason ? { commandReason } : {}),
    ...(serverDiagnostics ? { serverDiagnostics } : {}),
    outcomes
  };
}

export function normalizeBattleCommandAPIMessagePayload(payload: unknown): BattleCommandAPIMessagePayload | null {
  const accepted = normalizeBattleCommandAccepted(payload);
  if (accepted) {
    return { kind: "accepted", accepted };
  }

  const errorCode = readCommandSubmitErrorCode(payload);
  return errorCode ? { kind: "error", errorCode } : { kind: "error" };
}

function normalizeBattleCommandStatus(payload: unknown): AuthoritativeBattleCommandStatus | null {
  return payload === "applied" || payload === "ignored" ? payload : null;
}

function normalizeBattleCommandAcceptPath(payload: unknown): BattleCommandAcceptPathDto | null {
  return payload === "fresh" || payload === "serialized" ? payload : null;
}

function normalizeBattleCommandServerDiagnostics(
  payload: unknown
): BattleCommandServerDiagnosticsResponseDto | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<BattleCommandServerDiagnosticsResponseDto> & Record<string, unknown>;
  const path = normalizeBattleCommandAcceptPath(value.path);
  const receivedAt = readNumber(value.receivedAt);
  const completedAt = readNumber(value.completedAt);
  const durationMs = readNumber(value.durationMs);
  const lockWaitMs = readNumber(value.lockWaitMs);
  const lockHeldMs = readNumber(value.lockHeldMs);
  const advanceMs = readNumber(value.advanceMs);
  const commitRetryCount = readNumber(value.commitRetryCount);
  const clientTick = readNumber(value.clientTick);
  const acceptedTick = readNumber(value.acceptedTick);
  const acceptedTickLag = readNumber(value.acceptedTickLag);
  const clientCommandSeq = readNumber(value.clientCommandSeq);
  const acceptedCommandSeq = readNumber(value.acceptedCommandSeq);
  const acceptedCommandSeqLag = readNumber(value.acceptedCommandSeqLag);

  if (
    path === null ||
    receivedAt === null ||
    completedAt === null ||
    durationMs === null ||
    lockWaitMs === null ||
    lockHeldMs === null ||
    advanceMs === null ||
    commitRetryCount === null ||
    clientTick === null ||
    acceptedTick === null ||
    acceptedTickLag === null ||
    clientCommandSeq === null ||
    acceptedCommandSeq === null ||
    acceptedCommandSeqLag === null
  ) {
    return null;
  }

  return {
    path,
    receivedAt,
    completedAt,
    durationMs: Math.max(0, durationMs),
    lockWaitMs: Math.max(0, lockWaitMs),
    lockHeldMs: Math.max(0, lockHeldMs),
    advanceMs: Math.max(0, advanceMs),
    commitRetryCount: Math.max(0, Math.trunc(commitRetryCount)),
    clientTick: Math.max(0, Math.trunc(clientTick)),
    acceptedTick: Math.max(0, Math.trunc(acceptedTick)),
    acceptedTickLag,
    clientCommandSeq: Math.max(0, Math.trunc(clientCommandSeq)),
    acceptedCommandSeq: Math.max(0, Math.trunc(acceptedCommandSeq)),
    acceptedCommandSeqLag
  };
}

function normalizeBattleCommandReason(payload: unknown): AuthoritativeBattleCommandReason | null {
  return payload === "battle_finished" || payload === "battle_inactive" || payload === "player_dead" ? payload : null;
}

function normalizeBattleSkillOutcome(payload: unknown): AuthoritativeBattleSkillOutcome | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleSkillOutcome> & Record<string, unknown>;
  const action = normalizeAuthoritativeBattleSkillKind(value.action);
  const status = normalizeBattleSkillOutcomeStatus(value.status);
  const hasReason = Object.prototype.hasOwnProperty.call(value, "reason");
  const reason = normalizeBattleSkillOutcomeReason(value.reason);
  if (action === null || status === null || (hasReason && reason === null)) {
    return null;
  }

  return {
    action,
    status,
    ...(reason ? { reason } : {})
  };
}

function normalizeBattleSkillOutcomeStatus(payload: unknown): AuthoritativeBattleSkillOutcomeStatus | null {
  return payload === "applied" || payload === "noop" ? payload : null;
}

function normalizeBattleSkillOutcomeReason(payload: unknown): AuthoritativeBattleSkillOutcomeReason | null {
  return payload === "skill_not_owned" ||
    payload === "cooldown" ||
    payload === "missing_target" ||
    payload === "out_of_range" ||
    payload === "invalid_target" ||
    payload === "no_direction" ||
    payload === "blocked" ||
    payload === "insufficient_stamina"
    ? payload
    : null;
}

function readCommandSubmitErrorCode(payload: unknown): string | undefined {
  if (!payload || typeof payload !== "object") {
    return undefined;
  }

  const value = payload as Record<string, unknown>;
  const error = value.error;
  if (typeof error === "string" && error.trim()) {
    return error.trim();
  }

  const message = value.message;
  return typeof message === "string" && message.trim() ? message.trim() : undefined;
}
