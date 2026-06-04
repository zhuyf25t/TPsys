import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import {
  loadAuthoritativeBattleState,
  type AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type {
  finalizeLocalBattle,
  LocalBattleReturnSummary
} from "../../../runtime/battle/local/state/battleLocalGateway";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import type { ActiveBattleSession, ActiveBattleSessionOwner, MatchPhase } from "../objects/BattlePageState";
import {
  clearActiveBattleSession,
  writeActiveBattleSession,
  writeCompletedActiveBattleSession
} from "../stores/activeBattleSessionStore";
import { AUTHORITATIVE_RESULT_READY_RETRY_MS } from "../objects/BattlePageRuntimeConfig";
import { resolveCompletedSessionRecoveryPlan } from "./battleRuntimeFinalizationPlans";
import {
  type AuthoritativeSessionRestoreIdentity,
  isSharedAuthoritativeActiveSession,
  resolveAuthoritativeSessionRestoreIdentity
} from "./authoritativeSessionRestore";

interface MutableRef<T> {
  current: T;
}

type StartBattleRuntime = (
  runtimeInitialSnapshot?: GameSnapshot | null,
  restoredReplayFrames?: ReplayFrame[],
  restoredLastReplaySampleElapsed?: number | null,
  initialParticipants?: BattleInitialParticipantsConfig,
  battleId?: string,
  initialAuthoritativeState?: AuthoritativeBattleState | null,
  sharedAuthoritativeRuntime?: boolean
) => void;

interface BattleCompletedSessionRecoveryControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly runtimeHandleRef: MutableRef<BattleRuntimeHandle | null>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly authoritativeBattleStateRef: MutableRef<AuthoritativeBattleState | null>;
  readonly isQueueJoinCancelled: () => boolean;
  readonly applyRecoveredAuthoritativeIdentity: (identity: AuthoritativeSessionRestoreIdentity) => void;
  readonly startBattleRuntime: StartBattleRuntime;
  readonly loadAuthoritativeResultSummaryForBattle: (battleId: string) => Promise<LocalBattleReturnSummary | null>;
  readonly settleAuthoritativeResult: (summary: LocalBattleReturnSummary, battleId: string) => void;
  readonly finalizeLocalBattleWithFreshIdFallback: (
    input: Parameters<typeof finalizeLocalBattle>[0]
  ) => ReturnType<typeof finalizeLocalBattle>;
  readonly setMatchPhase: (phase: MatchPhase) => void;
  readonly setCurrentResultSummary: (summary: LocalBattleReturnSummary | null) => void;
  readonly setCurrentReplayId: (replayId: string | null) => void;
}

export function createBattleCompletedSessionRecoveryController({
  owner,
  runtimeHandleRef,
  finalizedRef,
  battleIdRef,
  authoritativeBattleStateRef,
  isQueueJoinCancelled,
  applyRecoveredAuthoritativeIdentity,
  startBattleRuntime,
  loadAuthoritativeResultSummaryForBattle,
  settleAuthoritativeResult,
  finalizeLocalBattleWithFreshIdFallback,
  setMatchPhase,
  setCurrentResultSummary,
  setCurrentReplayId
}: BattleCompletedSessionRecoveryControllerOptions) {
  const recoverSharedAuthoritativeCompletedSession = async (session: ActiveBattleSession): Promise<void> => {
    const restoreIdentity = resolveAuthoritativeSessionRestoreIdentity(session);
    if (!restoreIdentity.battleId) {
      clearActiveBattleSession(owner);
      return;
    }

    applyRecoveredAuthoritativeIdentity(restoreIdentity);
    battleIdRef.current = restoreIdentity.battleId;
    setMatchPhase("playing");
    writeActiveBattleSession({ ...session, savedAt: Date.now() });

    while (!isQueueJoinCancelled() && !finalizedRef.current) {
      const summary = await loadAuthoritativeResultSummaryForBattle(restoreIdentity.battleId);
      if (summary && !isQueueJoinCancelled() && !finalizedRef.current) {
        settleAuthoritativeResult(summary, restoreIdentity.battleId);
        return;
      }

      if (!runtimeHandleRef.current && !isQueueJoinCancelled() && !finalizedRef.current) {
        const state = authoritativeBattleStateRef.current?.battleId === restoreIdentity.battleId
          ? authoritativeBattleStateRef.current
          : await loadAuthoritativeBattleState(restoreIdentity.battleId);
        if (state && !runtimeHandleRef.current && !isQueueJoinCancelled() && !finalizedRef.current) {
          startBattleRuntime(
            session.snapshot,
            session.replayFrames,
            session.lastReplaySampleElapsed,
            undefined,
            state.battleId,
            state,
            true
          );
        }
      }

      await new Promise<void>((resolve) => window.setTimeout(resolve, AUTHORITATIVE_RESULT_READY_RETRY_MS));
    }
  };

  const recoverCompletedSession = (session: ActiveBattleSession): void => {
    if (isSharedAuthoritativeActiveSession(session)) {
      void recoverSharedAuthoritativeCompletedSession(session);
      return;
    }

    const recoveryPlan = resolveCompletedSessionRecoveryPlan(session);
    const finalized = finalizeLocalBattleWithFreshIdFallback({
      battleId: session.battleId,
      snapshot: recoveryPlan.finalSnapshot,
      finishedAt: Date.now(),
      thumbnailDataUrl: null,
      replayFrames: recoveryPlan.replayFrames,
      botOnlyClosure: recoveryPlan.botOnlyClosure,
      allowBotOnlyClosure: recoveryPlan.allowBotOnlyClosure,
      syncBackend: recoveryPlan.syncBackend
    });

    if (!finalized) {
      writeCompletedActiveBattleSession({
        ...session,
        savedAt: Date.now(),
        snapshot: recoveryPlan.finalSnapshot,
        replayFrames: recoveryPlan.replayFrames
      });
      return;
    }

    clearActiveBattleSession(owner);
    finalizedRef.current = true;
    setMatchPhase("settled");
    setCurrentResultSummary(finalized.returnSummary);
    setCurrentReplayId(finalized.replay.id);
  };

  return {
    recoverCompletedSession
  };
}
