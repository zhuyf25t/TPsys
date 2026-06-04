import type {
  AuthoritativeBattleCommandAccepted,
  AuthoritativeBattleCommandReason,
  AuthoritativeBattleCommandStatus,
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
    (hasCommandReason && commandReason === null)
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

  const error = (payload as Record<string, unknown>).error;
  return typeof error === "string" && error.trim() ? error.trim() : undefined;
}
