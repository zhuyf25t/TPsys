import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import { createBattleRuntime, type BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import { BATTLE_MATCH_DURATION_MS } from "../../../runtime/battle/local/state/battleLocalGateway";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { REPLAY_SAMPLE_INTERVAL_MS } from "../../../runtime/battle/microservices/projections/functions/BattleReplayFrameRecorder";
import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { ActiveBattleSession, BattleDrawerId, MatchPhase } from "../objects/BattlePageState";
import { BATTLE_COMPLETION_CHECK_INTERVAL_MS } from "./battlePageRuntimeHelpers";

interface MutableRef<T> {
  current: T;
}

type FinalizeRuntime = (
  forceTimeLimit?: boolean,
  forceCurrentSnapshot?: boolean,
  preserveCompletedSession?: boolean
) => void;

export type StartBattleRuntime = (
  runtimeInitialSnapshot?: GameSnapshot | null,
  restoredReplayFrames?: ReplayFrame[],
  restoredLastReplaySampleElapsed?: number | null,
  initialParticipants?: BattleInitialParticipantsConfig,
  battleId?: string,
  initialAuthoritativeState?: AuthoritativeBattleState | null,
  sharedAuthoritativeRuntime?: boolean
) => void;

interface BattleRuntimeLaunchAuthoritativeBridge {
  readonly applyInitialAuthoritativeBattleState: (state: AuthoritativeBattleState) => void;
  readonly setAuthoritativePreparedSkill: (preparedSkill: PreparedSkill) => void;
  readonly startAuthoritativeBattleBridge: () => void;
  readonly stopAuthoritativeBattleBridge: () => void;
}

interface BattleRuntimeLaunchControllerOptions {
  readonly runtimeRootRef: MutableRef<HTMLDivElement | null>;
  readonly hudRootRef: MutableRef<HTMLDivElement | null>;
  readonly runtimeHandleRef: MutableRef<BattleRuntimeHandle | null>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly queueStateRef: MutableRef<MatchmakingQueueState | null>;
  readonly sharedAuthoritativeRuntimeRef: MutableRef<boolean>;
  readonly authoritativeFirstFrameAppliedRef: MutableRef<boolean>;
  readonly authoritativePreparedSkillRef: MutableRef<PreparedSkill>;
  readonly replayFramesRef: MutableRef<ReplayFrame[]>;
  readonly lastReplaySampleFrameRef: MutableRef<ReplayFrame | null>;
  readonly lastReplaySampleElapsedRef: MutableRef<number | null>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly battleDurationDeadlineRef: MutableRef<number | null>;
  readonly snapshotTimerRef: MutableRef<number | null>;
  readonly battleEndTimerRef: MutableRef<number | null>;
  readonly battleDurationTimerRef: MutableRef<number | null>;
  readonly readRestoredActiveSession: () => ActiveBattleSession | null;
  readonly resolveLocalAuthoritativePlayerId: () => string;
  readonly resolveRuntimeBattleId: () => string;
  readonly resolveRuntimeMapId: (
    queueState?: MatchmakingQueueState | null,
    authoritativeState?: AuthoritativeBattleState | null,
    restoredSession?: ActiveBattleSession | null
  ) => string;
  readonly readRuntimeSnapshot: () => GameSnapshot | null;
  readonly pushReplayFrame: (snapshot: GameSnapshot, force?: boolean) => void;
  readonly persistRuntime: (forceReplayFrame?: boolean, snapshotOverride?: GameSnapshot | null) => void;
  readonly isRuntimeBattleComplete: (snapshot: GameSnapshot | null) => snapshot is GameSnapshot;
  readonly isBattleDurationExpired: () => boolean;
  readonly isAuthoritativeDurationExpired: () => boolean;
  readonly finalizeRuntime: FinalizeRuntime;
  readonly authoritativeBridge: BattleRuntimeLaunchAuthoritativeBridge;
  readonly setActiveDrawer: (drawer: BattleDrawerId | null) => void;
  readonly setMatchPhase: (phase: MatchPhase) => void;
}

export function createBattleRuntimeLaunchController({
  runtimeRootRef,
  hudRootRef,
  runtimeHandleRef,
  finalizedRef,
  queueStateRef,
  sharedAuthoritativeRuntimeRef,
  authoritativeFirstFrameAppliedRef,
  authoritativePreparedSkillRef,
  replayFramesRef,
  lastReplaySampleFrameRef,
  lastReplaySampleElapsedRef,
  battleIdRef,
  battleDurationDeadlineRef,
  snapshotTimerRef,
  battleEndTimerRef,
  battleDurationTimerRef,
  readRestoredActiveSession,
  resolveLocalAuthoritativePlayerId,
  resolveRuntimeBattleId,
  resolveRuntimeMapId,
  readRuntimeSnapshot,
  pushReplayFrame,
  persistRuntime,
  isRuntimeBattleComplete,
  isBattleDurationExpired,
  isAuthoritativeDurationExpired,
  finalizeRuntime,
  authoritativeBridge,
  setActiveDrawer,
  setMatchPhase
}: BattleRuntimeLaunchControllerOptions): StartBattleRuntime {
  return (
    runtimeInitialSnapshot: GameSnapshot | null = null,
    restoredReplayFrames: ReplayFrame[] = [],
    restoredLastReplaySampleElapsed: number | null = null,
    initialParticipants?: BattleInitialParticipantsConfig,
    battleId?: string,
    initialAuthoritativeState: AuthoritativeBattleState | null = null,
    sharedAuthoritativeRuntime = false
  ): void => {
    if (!runtimeRootRef.current || !hudRootRef.current || runtimeHandleRef.current) {
      return;
    }

    const runtime = createBattleRuntime({
      mountNode: runtimeRootRef.current,
      hudRoot: hudRootRef.current,
      initialSnapshot: runtimeInitialSnapshot,
      initialParticipants,
      initialAuthoritativeState,
      localAuthoritativePlayerId: resolveLocalAuthoritativePlayerId(),
      sharedAuthoritativeRuntime,
      mapId: resolveRuntimeMapId(queueStateRef.current, initialAuthoritativeState, readRestoredActiveSession())
    });

    runtimeHandleRef.current = runtime;
    sharedAuthoritativeRuntimeRef.current = sharedAuthoritativeRuntime;
    authoritativeFirstFrameAppliedRef.current = !sharedAuthoritativeRuntime;
    authoritativeBridge.setAuthoritativePreparedSkill(authoritativePreparedSkillRef.current);
    battleIdRef.current = initialAuthoritativeState?.battleId ?? battleId ?? resolveRuntimeBattleId();
    setActiveDrawer(null);
    setMatchPhase("playing");
    replayFramesRef.current = [...restoredReplayFrames];
    lastReplaySampleFrameRef.current = null;
    lastReplaySampleElapsedRef.current = restoredLastReplaySampleElapsed;

    if (initialAuthoritativeState) {
      authoritativeBridge.applyInitialAuthoritativeBattleState(initialAuthoritativeState);
    }

    const initialSnapshot =
      sharedAuthoritativeRuntime && !authoritativeFirstFrameAppliedRef.current ? null : readRuntimeSnapshot();
    if (initialSnapshot && replayFramesRef.current.length === 0) {
      pushReplayFrame(initialSnapshot, true);
    }
    if (initialSnapshot) {
      persistRuntime(false, initialSnapshot);
      setMatchPhase("playing");
    }

    const initialElapsedMs = Math.max(0, initialAuthoritativeState?.elapsedMs ?? initialSnapshot?.elapsedMs ?? 0);
    const battleDurationMs = Math.max(1, initialAuthoritativeState?.durationMs ?? BATTLE_MATCH_DURATION_MS);
    const battleDurationRemainingMs = Math.max(0, battleDurationMs - initialElapsedMs);
    battleDurationDeadlineRef.current = sharedAuthoritativeRuntime ? null : performance.now() + battleDurationRemainingMs;
    if (sharedAuthoritativeRuntime) {
      authoritativeBridge.startAuthoritativeBattleBridge();
    } else {
      authoritativeBridge.stopAuthoritativeBattleBridge();
    }

    snapshotTimerRef.current = window.setInterval(() => {
      const currentRuntime = runtimeHandleRef.current;
      if (!currentRuntime || finalizedRef.current) {
        return;
      }

      if (sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current) {
        return;
      }

      const snapshot = readRuntimeSnapshot();
      if (!snapshot) {
        return;
      }

      pushReplayFrame(snapshot);
      persistRuntime(false, snapshot);
      setMatchPhase("playing");
      if (isRuntimeBattleComplete(snapshot)) {
        finalizeRuntime(isAuthoritativeDurationExpired());
      } else if (isBattleDurationExpired()) {
        finalizeRuntime(true);
      }
    }, REPLAY_SAMPLE_INTERVAL_MS);

    battleEndTimerRef.current = window.setInterval(() => {
      finalizeRuntime(isAuthoritativeDurationExpired());
      if (!finalizedRef.current && isBattleDurationExpired()) {
        finalizeRuntime(true);
      }
    }, BATTLE_COMPLETION_CHECK_INTERVAL_MS);

    if (!sharedAuthoritativeRuntime) {
      battleDurationTimerRef.current = window.setTimeout(() => {
        finalizeRuntime(true);
      }, battleDurationRemainingMs);
    }

    window.setTimeout(() => {
      const snapshot = readRuntimeSnapshot();
      if (snapshot) {
        persistRuntime(true, snapshot);
        setMatchPhase("playing");
      }
    }, 0);
    finalizeRuntime(isBattleDurationExpired());
  };
}
