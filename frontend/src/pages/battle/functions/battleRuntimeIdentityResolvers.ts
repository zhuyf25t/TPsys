import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import {
  resolveMapIdForBattleMode,
  type BattlePlayModeId
} from "../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import type { ActiveBattleSession } from "../objects/BattlePageState";
import {
  createLocalBattleId,
  requiresAuthoritativeBattleId,
  resolveBackendBattleId
} from "./battlePageRuntimeHelpers";

export interface BattleRuntimeBattleIdInput {
  activeBattleId: string | null | undefined;
  queueState: MatchmakingQueueState | null;
}

export interface BattleRuntimeMapIdInput {
  authoritativeState: AuthoritativeBattleState | null;
  queueState: MatchmakingQueueState | null;
  restoredSession: ActiveBattleSession | null;
  selectedBattleModeId: BattlePlayModeId;
}

export interface AuthoritativeRuntimeBattleIdInput {
  activeBattleId: string | null | undefined;
  queueState: MatchmakingQueueState | null;
}

export interface AuthoritativeStartupRequirementInput {
  backendQueueJoinPending: boolean;
  queueState: MatchmakingQueueState | null;
}

export interface ScheduledAuthoritativeStartupRequirementInput {
  backendQueueJoinPending: boolean;
  queueTicketId: string | null | undefined;
  queueState: MatchmakingQueueState | null;
  previousQueueState: MatchmakingQueueState | null;
}

export function resolveBattleRuntimeBattleId({
  activeBattleId,
  queueState
}: BattleRuntimeBattleIdInput): string {
  return activeBattleId ?? resolveBackendBattleId(queueState) ?? createLocalBattleId();
}

export function resolveBattleRuntimeMapId({
  authoritativeState,
  queueState,
  restoredSession,
  selectedBattleModeId
}: BattleRuntimeMapIdInput): string {
  return (
    authoritativeState?.mapId?.trim() ||
    restoredSession?.mapId?.trim() ||
    queueState?.battleSession?.mapId?.trim() ||
    queueState?.mapId?.trim() ||
    resolveMapIdForBattleMode(selectedBattleModeId)
  );
}

export function resolveAuthoritativeRuntimeBattleId({
  activeBattleId,
  queueState
}: AuthoritativeRuntimeBattleIdInput): string | null {
  const retainedBattleId = activeBattleId?.trim();
  if (retainedBattleId) {
    return retainedBattleId;
  }

  const backendBattleId = resolveBackendBattleId(queueState);
  if (backendBattleId) {
    return backendBattleId;
  }

  if (requiresAuthoritativeBattleId(queueState)) {
    return null;
  }

  return createLocalBattleId();
}

export function requiresAuthoritativeStartup({
  backendQueueJoinPending,
  queueState
}: AuthoritativeStartupRequirementInput): boolean {
  return backendQueueJoinPending || requiresAuthoritativeBattleId(queueState);
}

export function requiresScheduledAuthoritativeStartup({
  backendQueueJoinPending,
  queueTicketId,
  queueState,
  previousQueueState
}: ScheduledAuthoritativeStartupRequirementInput): boolean {
  const retainedTicketId = queueTicketId?.trim();
  return (
    backendQueueJoinPending ||
    Boolean(retainedTicketId) ||
    requiresAuthoritativeBattleId(queueState) ||
    requiresAuthoritativeBattleId(previousQueueState)
  );
}
