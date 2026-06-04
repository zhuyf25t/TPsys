import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { LocalBattleReturnSummary } from "../../../runtime/battle/local/state/battleLocalGateway";

export type BattleRuntimePersistencePlan =
  | {
      readonly kind: "skip";
      readonly reason:
        | "runtime_inactive"
        | "finalized"
        | "authoritative_first_frame_pending"
        | "missing_snapshot"
        | "persist_interval_pending";
    }
  | {
      readonly kind: "write_completed";
      readonly snapshot: GameSnapshot;
      readonly captureReplayFrame: boolean;
    }
  | {
      readonly kind: "write_active";
      readonly snapshot: GameSnapshot;
      readonly captureReplayFrame: boolean;
      readonly persistedAt: number;
    };

export type BattleRuntimeFinalizationDecision =
  | {
      readonly kind: "skip";
      readonly reason:
        | "runtime_inactive"
        | "finalized"
        | "authoritative_first_frame_pending"
        | "missing_snapshot"
        | "not_finalizable";
    }
  | { readonly kind: "finalize_shared"; readonly snapshot: GameSnapshot }
  | {
      readonly kind: "finalize_local";
      readonly snapshot: GameSnapshot;
      readonly durationExpired: boolean;
      readonly forceCurrentSnapshot: boolean;
    };

export type SharedAuthoritativeFinalizationStartDecision =
  | { readonly kind: "start"; readonly battleId: string }
  | { readonly kind: "skip"; readonly reason: "missing_battle_id" | "request_in_flight" | "finalized" };

export type SharedAuthoritativeFinalizationResultPlan =
  | { readonly kind: "settle"; readonly summary: LocalBattleReturnSummary; readonly battleId: string }
  | { readonly kind: "preserve_active_session" }
  | { readonly kind: "skip"; readonly reason: "cancelled" | "finalized" };

interface ResolveBattleRuntimePersistencePlanInput {
  readonly runtimeActive: boolean;
  readonly finalized: boolean;
  readonly authoritativeFirstFramePending: boolean;
  readonly snapshot: GameSnapshot | null;
  readonly forceReplayFrame: boolean;
  readonly shouldStoreCompletedSession: boolean;
  readonly now: number;
  readonly lastPersistedAt: number;
  readonly persistIntervalMs: number;
}

interface ResolveBattleRuntimeFinalizationDecisionInput {
  readonly runtimeActive: boolean;
  readonly finalized: boolean;
  readonly authoritativeFirstFramePending: boolean;
  readonly snapshot: GameSnapshot | null;
  readonly shouldFinalizeSnapshot: boolean;
  readonly sharedAuthoritativeRuntime: boolean;
  readonly durationExpired: boolean;
  readonly forceCurrentSnapshot: boolean;
}

interface ResolveSharedAuthoritativeFinalizationStartDecisionInput {
  readonly battleId: string | null | undefined;
  readonly requestInFlight: boolean;
  readonly finalized: boolean;
}

interface ResolveSharedAuthoritativeFinalizationResultPlanInput {
  readonly battleId: string;
  readonly summary: LocalBattleReturnSummary | null;
  readonly cancelled: boolean;
  readonly finalized: boolean;
}

export function resolveBattleRuntimePersistencePlan({
  runtimeActive,
  finalized,
  authoritativeFirstFramePending,
  snapshot,
  forceReplayFrame,
  shouldStoreCompletedSession,
  now,
  lastPersistedAt,
  persistIntervalMs
}: ResolveBattleRuntimePersistencePlanInput): BattleRuntimePersistencePlan {
  if (!runtimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }
  if (authoritativeFirstFramePending) {
    return { kind: "skip", reason: "authoritative_first_frame_pending" };
  }
  if (!snapshot) {
    return { kind: "skip", reason: "missing_snapshot" };
  }
  if (shouldStoreCompletedSession) {
    return { kind: "write_completed", snapshot, captureReplayFrame: forceReplayFrame };
  }
  if (!forceReplayFrame && now - lastPersistedAt < persistIntervalMs) {
    return { kind: "skip", reason: "persist_interval_pending" };
  }

  return { kind: "write_active", snapshot, captureReplayFrame: forceReplayFrame, persistedAt: now };
}

export function resolveBattleRuntimeFinalizationDecision({
  runtimeActive,
  finalized,
  authoritativeFirstFramePending,
  snapshot,
  shouldFinalizeSnapshot,
  sharedAuthoritativeRuntime,
  durationExpired,
  forceCurrentSnapshot
}: ResolveBattleRuntimeFinalizationDecisionInput): BattleRuntimeFinalizationDecision {
  if (!runtimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }
  if (authoritativeFirstFramePending) {
    return { kind: "skip", reason: "authoritative_first_frame_pending" };
  }
  if (!snapshot) {
    return { kind: "skip", reason: "missing_snapshot" };
  }
  if (!shouldFinalizeSnapshot) {
    return { kind: "skip", reason: "not_finalizable" };
  }
  if (sharedAuthoritativeRuntime) {
    return { kind: "finalize_shared", snapshot };
  }

  return { kind: "finalize_local", snapshot, durationExpired, forceCurrentSnapshot };
}

export function resolveSharedAuthoritativeFinalizationStartDecision({
  battleId,
  requestInFlight,
  finalized
}: ResolveSharedAuthoritativeFinalizationStartDecisionInput): SharedAuthoritativeFinalizationStartDecision {
  const normalizedBattleId = battleId?.trim() ?? "";
  if (!normalizedBattleId) {
    return { kind: "skip", reason: "missing_battle_id" };
  }
  if (requestInFlight) {
    return { kind: "skip", reason: "request_in_flight" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }

  return { kind: "start", battleId: normalizedBattleId };
}

export function resolveSharedAuthoritativeFinalizationResultPlan({
  battleId,
  summary,
  cancelled,
  finalized
}: ResolveSharedAuthoritativeFinalizationResultPlanInput): SharedAuthoritativeFinalizationResultPlan {
  if (cancelled) {
    return { kind: "skip", reason: "cancelled" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }
  if (!summary) {
    return { kind: "preserve_active_session" };
  }

  return { kind: "settle", summary, battleId };
}
