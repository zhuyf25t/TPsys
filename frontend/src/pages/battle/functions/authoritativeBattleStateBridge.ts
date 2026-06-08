import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";

export type AuthoritativeStatePollDecision =
  | { readonly kind: "poll"; readonly battleId: string }
  | { readonly kind: "skip"; readonly reason: "runtime_inactive" | "missing_battle_id" | "request_in_flight" };

export type AuthoritativeStatePollResultDecision =
  | { readonly kind: "apply"; readonly state: AuthoritativeBattleState }
  | { readonly kind: "skip"; readonly reason: "missing_state" | "finalized" };

export type AuthoritativeStateApplicationDecision =
  | { readonly kind: "apply"; readonly state: AuthoritativeBattleState }
  | { readonly kind: "skip"; readonly reason: "stale_state" };

export type AuthoritativeStatePollingTimerDecision =
  | { readonly kind: "start" }
  | { readonly kind: "skip"; readonly reason: "timer_active" };

export type AuthoritativeStateStreamStartupPlan =
  | { readonly kind: "open_stream"; readonly battleId: string }
  | { readonly kind: "poll" };

export type AuthoritativeStateStreamFallbackDecision =
  | { readonly kind: "poll" }
  | { readonly kind: "skip"; readonly reason: "runtime_inactive" | "finalized" };

interface AuthoritativeStatePollDecisionInput {
  readonly sharedRuntimeActive: boolean;
  readonly battleId: string | null | undefined;
  readonly requestInFlight: boolean;
}

interface AuthoritativeStatePollResultDecisionInput {
  readonly state: AuthoritativeBattleState | null;
  readonly finalized: boolean;
}

interface AuthoritativeStateApplicationDecisionInput {
  readonly state: AuthoritativeBattleState;
  readonly lastAppliedState: AuthoritativeBattleState | null;
}

interface AuthoritativeStatePollingTimerDecisionInput {
  readonly timerActive: boolean;
}

interface AuthoritativeStateStreamStartupPlanInput {
  readonly battleId: string | null | undefined;
}

interface AuthoritativeStateStreamFallbackDecisionInput {
  readonly sharedRuntimeActive: boolean;
  readonly finalized: boolean;
}

export function resolveAuthoritativeStatePollDecision({
  sharedRuntimeActive,
  battleId,
  requestInFlight
}: AuthoritativeStatePollDecisionInput): AuthoritativeStatePollDecision {
  const normalizedBattleId = battleId?.trim() ?? "";
  if (!sharedRuntimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }
  if (!normalizedBattleId) {
    return { kind: "skip", reason: "missing_battle_id" };
  }
  if (requestInFlight) {
    return { kind: "skip", reason: "request_in_flight" };
  }

  return { kind: "poll", battleId: normalizedBattleId };
}

export function resolveAuthoritativeStatePollResultDecision({
  state,
  finalized
}: AuthoritativeStatePollResultDecisionInput): AuthoritativeStatePollResultDecision {
  if (!state) {
    return { kind: "skip", reason: "missing_state" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }

  return { kind: "apply", state };
}

export function resolveAuthoritativeStateApplicationDecision({
  state,
  lastAppliedState
}: AuthoritativeStateApplicationDecisionInput): AuthoritativeStateApplicationDecision {
  if (!lastAppliedState || state.battleId !== lastAppliedState.battleId) {
    return { kind: "apply", state };
  }

  if (state.tick < lastAppliedState.tick) {
    return { kind: "skip", reason: "stale_state" };
  }
  if (state.tick > lastAppliedState.tick) {
    return { kind: "apply", state };
  }

  return state.elapsedMs > lastAppliedState.elapsedMs
    ? { kind: "apply", state }
    : { kind: "skip", reason: "stale_state" };
}

export function resolveAuthoritativeStatePollingTimerDecision({
  timerActive
}: AuthoritativeStatePollingTimerDecisionInput): AuthoritativeStatePollingTimerDecision {
  return timerActive ? { kind: "skip", reason: "timer_active" } : { kind: "start" };
}

export function resolveAuthoritativeStateStreamStartupPlan({
  battleId
}: AuthoritativeStateStreamStartupPlanInput): AuthoritativeStateStreamStartupPlan {
  const normalizedBattleId = battleId?.trim() ?? "";
  return normalizedBattleId ? { kind: "open_stream", battleId: normalizedBattleId } : { kind: "poll" };
}

export function resolveAuthoritativeStateStreamFallbackDecision({
  sharedRuntimeActive,
  finalized
}: AuthoritativeStateStreamFallbackDecisionInput): AuthoritativeStateStreamFallbackDecision {
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }
  if (!sharedRuntimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }

  return { kind: "poll" };
}
