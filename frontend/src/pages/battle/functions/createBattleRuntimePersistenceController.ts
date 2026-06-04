import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";
import { ACTIVE_SESSION_PERSIST_INTERVAL_MS } from "../objects/BattlePageRuntimeConfig";
import {
  writeActiveBattleSession,
  writeCompletedActiveBattleSession
} from "../stores/activeBattleSessionStore";
import { buildActiveBattleSession } from "./buildActiveBattleSession";
import { resolveBattleRuntimePersistencePlan } from "./battleRuntimeLifecyclePlans";
import { recordBattleReplayFrame } from "./battleRuntimeReplayFrames";

interface MutableRef<T> {
  current: T;
}

interface BattleRuntimePersistenceControllerOptions {
  readonly owner: ActiveBattleSessionOwner;
  readonly isRuntimeActive: () => boolean;
  readonly finalizedRef: MutableRef<boolean>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly activeSessionEpochRef: MutableRef<string | null>;
  readonly sharedAuthoritativeRuntimeRef: MutableRef<boolean>;
  readonly authoritativeFirstFrameAppliedRef: MutableRef<boolean>;
  readonly replayFramesRef: MutableRef<ReplayFrame[]>;
  readonly lastReplaySampleFrameRef: MutableRef<ReplayFrame | null>;
  readonly lastReplaySampleElapsedRef: MutableRef<number | null>;
  readonly lastActiveSessionPersistedAtRef: MutableRef<number>;
  readonly readRuntimeSnapshot: () => GameSnapshot | null;
  readonly resolveRuntimeBattleId: () => string;
  readonly resolveRuntimeMapId: () => string;
  readonly resolveLocalAuthoritativePlayerId: () => string;
  readonly resolveLocalAuthoritativeTicketId: () => string;
  readonly shouldStoreRuntimeCompletedSession: (snapshot: GameSnapshot) => boolean;
}

export function createBattleRuntimePersistenceController({
  owner,
  isRuntimeActive,
  finalizedRef,
  battleIdRef,
  activeSessionEpochRef,
  sharedAuthoritativeRuntimeRef,
  authoritativeFirstFrameAppliedRef,
  replayFramesRef,
  lastReplaySampleFrameRef,
  lastReplaySampleElapsedRef,
  lastActiveSessionPersistedAtRef,
  readRuntimeSnapshot,
  resolveRuntimeBattleId,
  resolveRuntimeMapId,
  resolveLocalAuthoritativePlayerId,
  resolveLocalAuthoritativeTicketId,
  shouldStoreRuntimeCompletedSession
}: BattleRuntimePersistenceControllerOptions) {
  const buildActiveSession = (snapshot: GameSnapshot): ActiveBattleSession => {
    const battleId = resolveRuntimeBattleId();
    battleIdRef.current = battleId;
    return buildActiveBattleSession({
      owner,
      sessionEpoch: activeSessionEpochRef.current,
      battleId,
      mapId: resolveRuntimeMapId(),
      sharedAuthoritativeRuntime: sharedAuthoritativeRuntimeRef.current,
      localAuthoritativePlayerId: resolveLocalAuthoritativePlayerId(),
      localAuthoritativeTicketId: resolveLocalAuthoritativeTicketId(),
      savedAt: Date.now(),
      snapshot,
      replayFrames: replayFramesRef.current,
      lastReplaySampleElapsed: lastReplaySampleElapsedRef.current
    });
  };

  const pushReplayFrame = (snapshot: GameSnapshot, force = false): void => {
    const nextReplayState = recordBattleReplayFrame({
      replayFrames: replayFramesRef.current,
      lastReplaySampleElapsed: lastReplaySampleElapsedRef.current,
      snapshot,
      force
    });
    if (!nextReplayState) {
      return;
    }

    replayFramesRef.current = nextReplayState.replayFrames;
    lastReplaySampleFrameRef.current = nextReplayState.lastReplaySampleFrame;
    lastReplaySampleElapsedRef.current = nextReplayState.lastReplaySampleElapsed;
  };

  const writeActiveSession = (snapshot: GameSnapshot): void => {
    writeActiveBattleSession(buildActiveSession(snapshot));
  };

  const writeCompletedSession = (snapshot: GameSnapshot): void => {
    writeCompletedActiveBattleSession(buildActiveSession(snapshot));
  };

  const persistRuntime = (forceReplayFrame = false, snapshotOverride: GameSnapshot | null = null): void => {
    const snapshot = snapshotOverride ?? readRuntimeSnapshot();
    const now = Date.now();
    const persistencePlan = resolveBattleRuntimePersistencePlan({
      runtimeActive: isRuntimeActive(),
      finalized: finalizedRef.current,
      authoritativeFirstFramePending: sharedAuthoritativeRuntimeRef.current && !authoritativeFirstFrameAppliedRef.current,
      snapshot,
      forceReplayFrame,
      shouldStoreCompletedSession: snapshot ? shouldStoreRuntimeCompletedSession(snapshot) : false,
      now,
      lastPersistedAt: lastActiveSessionPersistedAtRef.current,
      persistIntervalMs: ACTIVE_SESSION_PERSIST_INTERVAL_MS
    });
    if (persistencePlan.kind === "skip") {
      return;
    }

    if (persistencePlan.captureReplayFrame) {
      pushReplayFrame(persistencePlan.snapshot, true);
    }

    if (persistencePlan.kind === "write_completed") {
      writeCompletedSession(persistencePlan.snapshot);
      return;
    }

    writeActiveSession(persistencePlan.snapshot);
    lastActiveSessionPersistedAtRef.current = persistencePlan.persistedAt;
  };

  return {
    buildActiveSession,
    persistRuntime,
    pushReplayFrame,
    writeActiveSession,
    writeCompletedSession
  };
}
