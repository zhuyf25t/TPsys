import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";
import {
  clearActiveBattleSession,
  clearActiveBattleSessionProgress,
  consumeCompletedActiveBattleSession,
  publishActiveBattleSessionEpoch,
  readActiveBattleSession
} from "../stores/activeBattleSessionStore";
import { isActiveBattleSessionForLocalPlayer } from "./battlePageRuntimeHelpers";

interface MutableRef<T> {
  current: T;
}

interface InitializeBattlePageSessionProgressInput {
  readonly owner: ActiveBattleSessionOwner;
  readonly shouldStartNewBattle: boolean;
  readonly shouldRestoreActiveSession: boolean;
  readonly loadoutHandle: string;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly newBattleResetPendingRef: MutableRef<boolean>;
}

interface BattlePageInitialSessionProgress {
  readonly completedSession: ActiveBattleSession | null;
  readonly restoredActiveSession: ActiveBattleSession | null;
}

export function initializeBattlePageSessionProgress({
  owner,
  shouldStartNewBattle,
  shouldRestoreActiveSession,
  loadoutHandle,
  activeSessionEpochRef,
  newBattleResetPendingRef
}: InitializeBattlePageSessionProgressInput): BattlePageInitialSessionProgress {
  let completedSession: ActiveBattleSession | null = null;
  let restoredActiveSession: ActiveBattleSession | null = null;

  if (shouldStartNewBattle) {
    activeSessionEpochRef.current = publishActiveBattleSessionEpoch(owner);
    clearActiveBattleSession(owner);
    newBattleResetPendingRef.current = false;
  } else {
    completedSession = consumeCompletedActiveBattleSession(owner);
  }

  if (!completedSession) {
    const activeSession = readActiveBattleSession(owner);
    completedSession = consumeCompletedActiveBattleSession(owner);
    if (
      !completedSession &&
      activeSession &&
      shouldRestoreActiveSession &&
      isActiveBattleSessionForLocalPlayer(activeSession, loadoutHandle)
    ) {
      restoredActiveSession = activeSession;
      activeSessionEpochRef.current = activeSession.sessionEpoch ?? null;
    } else if (!completedSession && activeSession) {
      activeSessionEpochRef.current = publishActiveBattleSessionEpoch(owner);
      clearActiveBattleSessionProgress(owner);
    }
  }

  if (!completedSession && !restoredActiveSession && activeSessionEpochRef.current === null) {
    activeSessionEpochRef.current = publishActiveBattleSessionEpoch(owner);
  }

  return {
    completedSession,
    restoredActiveSession
  };
}
