import { loadBattleResultByBattleId } from "../../../apis/battle/microservices/results/api/BattleResultsApi";
import {
  loadAuthoritativeBattleState,
  type AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { LocalBattleReturnSummary } from "../../../runtime/battle/local/state/battleLocalGateway";
import { isAuthoritativeFinalResultReady } from "./authoritativeBattleStatePredicates";
import { toAuthoritativeResultSummary } from "./authoritativeResultSummary";

interface LoadAuthoritativeResultSummaryInput {
  readonly battleId: string;
  readonly ownerHandle: string;
  readonly timeoutMs: number;
  readonly retryMs: number;
  readonly isCancelled: () => boolean;
  readonly readCachedState: (battleId: string) => AuthoritativeBattleState | null;
  readonly writeCachedState: (state: AuthoritativeBattleState) => void;
}

export async function loadAuthoritativeResultSummaryWhenReady({
  battleId,
  ownerHandle,
  timeoutMs,
  retryMs,
  isCancelled,
  readCachedState,
  writeCachedState
}: LoadAuthoritativeResultSummaryInput): Promise<LocalBattleReturnSummary | null> {
  const normalizedBattleId = battleId.trim();
  if (!normalizedBattleId) {
    return null;
  }

  const deadline = performance.now() + timeoutMs;
  while (!isCancelled() && performance.now() <= deadline) {
    const cachedState = readCachedState(normalizedBattleId);
    const state = isAuthoritativeFinalResultReady(cachedState)
      ? cachedState
      : await loadAuthoritativeBattleState(normalizedBattleId);
    if (isCancelled()) {
      return null;
    }

    if (state) {
      writeCachedState(state);
    }

    if (isAuthoritativeFinalResultReady(state)) {
      const remoteRecord = await loadBattleResultByBattleId(normalizedBattleId, ownerHandle);
      if (isCancelled()) {
        return null;
      }

      if (remoteRecord?.battleId.trim() === normalizedBattleId) {
        return toAuthoritativeResultSummary(remoteRecord);
      }
    }

    await new Promise((resolve) => window.setTimeout(resolve, retryMs));
  }

  return null;
}
