import type { ActiveBattleSessionOwner } from "../objects/BattlePageState";
import { clearActiveBattleSession } from "../stores/activeBattleSessionStore";

interface MutableRef<T> {
  current: T;
}

type FinalizeRuntime = (
  forceTimeLimit?: boolean,
  forceCurrentSnapshot?: boolean,
  preserveCompletedSession?: boolean
) => void;

interface BattlePageExitPersistenceControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly discardSessionOnNextTeardownRef: MutableRef<boolean>;
  readonly newBattleResetPendingRef: MutableRef<boolean>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly shouldFinalizeRuntimeOnExit: () => boolean;
  readonly isBattleDurationExpired: () => boolean;
  readonly finalizeRuntime: FinalizeRuntime;
  readonly persistRuntime: (forceReplayFrame?: boolean, snapshotOverride?: null) => void;
}

export function createBattlePageExitPersistenceController({
  owner,
  discardSessionOnNextTeardownRef,
  newBattleResetPendingRef,
  finalizedRef,
  shouldFinalizeRuntimeOnExit,
  isBattleDurationExpired,
  finalizeRuntime,
  persistRuntime
}: BattlePageExitPersistenceControllerOptions): () => void {
  const persistRuntimeBeforePageExit = (): void => {
    if (discardSessionOnNextTeardownRef.current || newBattleResetPendingRef.current) {
      clearActiveBattleSession(owner);
      return;
    }

    const shouldFinalizeOnExit = shouldFinalizeRuntimeOnExit();
    if (shouldFinalizeOnExit) {
      finalizeRuntime(isBattleDurationExpired(), false, true);
      if (finalizedRef.current) {
        return;
      }
    }

    persistRuntime(true);
  };

  window.addEventListener("pagehide", persistRuntimeBeforePageExit);
  window.addEventListener("beforeunload", persistRuntimeBeforePageExit);

  return () => {
    window.removeEventListener("pagehide", persistRuntimeBeforePageExit);
    window.removeEventListener("beforeunload", persistRuntimeBeforePageExit);
  };
}
