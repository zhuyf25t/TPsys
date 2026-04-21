import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { GameSnapshot } from "../domain/types";
import {
  BATTLE_MATCH_DURATION_MS,
  finalizeLocalBattle,
  type LocalBattleReturnSummary
} from "../features/battle/local/battleLocalGateway";
import {
  clearActiveBattleSession,
  readActiveBattleSession,
  writeActiveBattleSession
} from "../features/battle/page/activeBattleSessionStore";
import { buildBattleDrawer } from "../features/battle/page/battleDrawerPresenter";
import { BattleSettlementOverlay } from "../features/battle/page/BattleSettlementOverlay";
import { MatchingOverlay } from "../features/battle/page/MatchingOverlay";
import {
  joinMatchmakingQueue,
  leaveMatchmakingQueue,
  loadMatchmakingQueueStatus
} from "../features/battle/page/matchmakingQueueGateway";
import type { MatchmakingQueueState } from "../features/battle/page/matchmakingQueueTypes";
import {
  isBattleComplete,
  MATCHMAKING_DURATION_MS,
  QUICK_LEFT,
  QUICK_RIGHT,
  type BattleDrawerId,
  type MatchPhase
} from "../features/battle/page/battlePageTypes";
import { createBattleRuntime, type BattleRuntimeHandle } from "../features/battle/renderer/createBattleRuntime";
import { getDiscussionSummaries } from "../features/forum/forumGateway";
import {
  getLoadoutPresets,
  getLoadoutStateVersion,
  getLoadoutSummary,
  setLoadoutPreset,
  subscribeLoadoutState
} from "../features/loadout/loadoutGateway";
import { getMailSummaries, loadMergedMailSummaries } from "../features/mails/mailsGateway";
import { getRatingEntries, loadRatingEntries } from "../features/rating/ratingGateway";
import { getReplaySummaries } from "../features/replay/replayGateway";
import { buildReplayFrame, REPLAY_SAMPLE_INTERVAL_MS, shouldCaptureReplayFrame } from "../features/replay/replayRecorder";
import type { ReplayFrame } from "../features/replay/replayTypes";
import { BattleChrome } from "../shared/ui/BattleChrome";
import { QuickPreviewOverlay } from "../shared/ui/QuickPreviewOverlay";
import { useLobbyData } from "../shared/ui/useLobbyData";

export function BattlePage() {
  const runtimeRootRef = useRef<HTMLDivElement | null>(null);
  const hudRootRef = useRef<HTMLDivElement | null>(null);
  const runtimeHandleRef = useRef<BattleRuntimeHandle | null>(null);
  const finalizedRef = useRef(false);
  const battleStartLockedRef = useRef(false);
  const countdownStartedAtRef = useRef<number | null>(null);
  const countdownTimerRef = useRef<number | null>(null);
  const matchStartTimerRef = useRef<number | null>(null);
  const queuePollingTimerRef = useRef<number | null>(null);
  const snapshotTimerRef = useRef<number | null>(null);
  const battleEndTimerRef = useRef<number | null>(null);
  const battleDurationTimerRef = useRef<number | null>(null);
  const replayFramesRef = useRef<ReplayFrame[]>([]);
  const lastReplaySampleFrameRef = useRef<ReplayFrame | null>(null);
  const lastReplaySampleElapsedRef = useRef<number | null>(null);
  const initialActiveSessionConsumedRef = useRef(false);

  const [hasNewBattleIntent] = useState(() => {
    if (typeof window === "undefined") {
      return false;
    }

    return new URLSearchParams(window.location.search).get("new") === "1";
  });
  const [initialActiveSession] = useState(() => {
    if (hasNewBattleIntent) {
      clearActiveBattleSession();
      return null;
    }

    return readActiveBattleSession();
  });
  const [matchNonce, setMatchNonce] = useState(0);
  const [matchCountdownMs, setMatchCountdownMs] = useState(MATCHMAKING_DURATION_MS);
  const [currentResultSummary, setCurrentResultSummary] = useState<LocalBattleReturnSummary | null>(null);
  const [currentReplayId, setCurrentReplayId] = useState<string | null>(null);
  const [matchPhase, setMatchPhase] = useState<MatchPhase>("matching");
  const [queueState, setQueueState] = useState<MatchmakingQueueState | null>(null);
  const [activeDrawer, setActiveDrawer] = useState<BattleDrawerId | null>(null);

  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  useSyncExternalStore(subscribeLoadoutState, getLoadoutStateVersion, getLoadoutStateVersion);
  const loadout = getLoadoutSummary();
  const presets = getLoadoutPresets();
  const replaySummaries = getReplaySummaries();
  const discussionSummaries = getDiscussionSummaries();
  const mailSummaries = useLobbyData(
    () => getMailSummaries(),
    () => loadMergedMailSummaries(currentUser?.handle),
    [currentUser?.handle]
  );
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [matchPhase, matchNonce]);

  useEffect(() => {
    if (!runtimeRootRef.current || !hudRootRef.current) {
      return;
    }

    finalizedRef.current = false;
    battleStartLockedRef.current = false;
    setCurrentResultSummary(null);
    setCurrentReplayId(null);
    setActiveDrawer(null);
    replayFramesRef.current = [];
    lastReplaySampleFrameRef.current = null;
    lastReplaySampleElapsedRef.current = null;

    const clearCountdownTimer = (): void => {
      if (countdownTimerRef.current !== null) {
        window.clearInterval(countdownTimerRef.current);
        countdownTimerRef.current = null;
      }
    };

    const clearMatchStartTimer = (): void => {
      if (matchStartTimerRef.current !== null) {
        window.clearTimeout(matchStartTimerRef.current);
        matchStartTimerRef.current = null;
      }
    };

    const clearQueuePollingTimer = (): void => {
      if (queuePollingTimerRef.current !== null) {
        window.clearInterval(queuePollingTimerRef.current);
        queuePollingTimerRef.current = null;
      }
    };

    const clearSnapshotTimer = (): void => {
      if (snapshotTimerRef.current !== null) {
        window.clearInterval(snapshotTimerRef.current);
        snapshotTimerRef.current = null;
      }
    };

    const clearBattleEndTimer = (): void => {
      if (battleEndTimerRef.current !== null) {
        window.clearInterval(battleEndTimerRef.current);
        battleEndTimerRef.current = null;
      }
    };

    const clearBattleDurationTimer = (): void => {
      if (battleDurationTimerRef.current !== null) {
        window.clearTimeout(battleDurationTimerRef.current);
        battleDurationTimerRef.current = null;
      }
    };

    const persistRuntime = (): void => {
      const runtime = runtimeHandleRef.current;
      if (!runtime || finalizedRef.current) {
        return;
      }

      const snapshot = runtime.readSnapshot();
      if (!snapshot) {
        return;
      }

      if (isBattleComplete(snapshot)) {
        clearActiveBattleSession();
        return;
      }

      writeActiveBattleSession({
        version: 1,
        savedAt: Date.now(),
        snapshot,
        replayFrames: replayFramesRef.current,
        lastReplaySampleElapsed: lastReplaySampleElapsedRef.current
      });
    };

    const destroyRuntime = (persist = true): void => {
      clearSnapshotTimer();
      clearBattleEndTimer();
      clearBattleDurationTimer();
      clearMatchStartTimer();
      clearQueuePollingTimer();
      if (persist) {
        finalizeRuntime();
        if (finalizedRef.current) {
          runtimeHandleRef.current?.destroy();
          runtimeHandleRef.current = null;
          return;
        }
        persistRuntime();
      }
      runtimeHandleRef.current?.destroy();
      runtimeHandleRef.current = null;
      battleStartLockedRef.current = false;
    };

    const pushReplayFrame = (snapshot: GameSnapshot, force = false): void => {
      if (!force && !shouldCaptureReplayFrame(lastReplaySampleElapsedRef.current, snapshot.elapsedMs)) {
        return;
      }

      const nextFrame = buildReplayFrame(snapshot);
      replayFramesRef.current.push(nextFrame);
      lastReplaySampleFrameRef.current = nextFrame;
      lastReplaySampleElapsedRef.current = snapshot.elapsedMs;
    };

    const finalizeRuntime = (): void => {
      const runtime = runtimeHandleRef.current;
      if (!runtime || finalizedRef.current) {
        return;
      }

      const snapshot = runtime.readSnapshot();
      if (!isBattleComplete(snapshot)) {
        return;
      }

      pushReplayFrame(snapshot, true);

      finalizedRef.current = true;
      clearActiveBattleSession();

      const finalized = finalizeLocalBattle({
        snapshot,
        finishedAt: Date.now(),
        thumbnailDataUrl: runtime.captureThumbnail(),
        replayFrames: replayFramesRef.current
      });

      destroyRuntime(false);
      setMatchPhase("settled");

      if (finalized) {
        setCurrentResultSummary(finalized.returnSummary);
        setCurrentReplayId(finalized.replay.id);
      }
    };

    const startBattleRuntime = (
      runtimeInitialSnapshot: GameSnapshot | null = null,
      restoredReplayFrames: ReplayFrame[] = [],
      restoredLastReplaySampleElapsed: number | null = null
    ): void => {
      if (!runtimeRootRef.current || !hudRootRef.current || runtimeHandleRef.current) {
        return;
      }

      const runtime = createBattleRuntime({
        mountNode: runtimeRootRef.current,
        hudRoot: hudRootRef.current,
        initialSnapshot: runtimeInitialSnapshot
      });

      runtimeHandleRef.current = runtime;
      setActiveDrawer(null);
      setMatchPhase("playing");
      replayFramesRef.current = [...restoredReplayFrames];
      lastReplaySampleFrameRef.current = null;
      lastReplaySampleElapsedRef.current = restoredLastReplaySampleElapsed;

      const initialSnapshot = runtime.readSnapshot();
      if (initialSnapshot && replayFramesRef.current.length === 0) {
        pushReplayFrame(initialSnapshot, true);
      }

      snapshotTimerRef.current = window.setInterval(() => {
        const currentRuntime = runtimeHandleRef.current;
        if (!currentRuntime || finalizedRef.current) {
          return;
        }

        const snapshot = currentRuntime.readSnapshot();
        if (!snapshot) {
          return;
        }

        pushReplayFrame(snapshot);
        persistRuntime();
        if (isBattleComplete(snapshot)) {
          finalizeRuntime();
        }
      }, REPLAY_SAMPLE_INTERVAL_MS);

      battleEndTimerRef.current = window.setInterval(() => {
        finalizeRuntime();
      }, 250);

      const initialElapsedMs = Math.max(0, initialSnapshot?.elapsedMs ?? 0);
      battleDurationTimerRef.current = window.setTimeout(() => {
        finalizeRuntime();
      }, Math.max(0, BATTLE_MATCH_DURATION_MS - initialElapsedMs));

      finalizeRuntime();
    };

    const activeSession = hasNewBattleIntent
      ? null
      : initialActiveSessionConsumedRef.current
        ? readActiveBattleSession()
        : initialActiveSession;
    initialActiveSessionConsumedRef.current = true;

    setMatchPhase("matching");
    setMatchCountdownMs(MATCHMAKING_DURATION_MS);
    setQueueState(null);
    countdownStartedAtRef.current = performance.now();
    let queueTicketId: string | null = null;
    let queueJoinCancelled = false;

    const startScheduledBattle = (): void => {
      if (battleStartLockedRef.current || finalizedRef.current) {
        return;
      }

      battleStartLockedRef.current = true;
      clearMatchStartTimer();
      clearCountdownTimer();
      clearQueuePollingTimer();
      setMatchCountdownMs(0);

      if (activeSession) {
        startBattleRuntime(activeSession.snapshot, activeSession.replayFrames, activeSession.lastReplaySampleElapsed);
        return;
      }

      startBattleRuntime();
    };

    matchStartTimerRef.current = window.setTimeout(() => {
      startScheduledBattle();
    }, MATCHMAKING_DURATION_MS);

    const tickCountdown = (): void => {
      const reference = countdownStartedAtRef.current ?? performance.now();
      const elapsed = performance.now() - reference;
      const remaining = Math.max(0, MATCHMAKING_DURATION_MS - elapsed);
      setMatchCountdownMs(remaining);
    };

    countdownTimerRef.current = window.setInterval(tickCountdown, 100);
    tickCountdown();

    const pollQueueStatus = async (ticketId: string): Promise<void> => {
      if (queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      const status = await loadMatchmakingQueueStatus(ticketId);
      if (!status || queueJoinCancelled || battleStartLockedRef.current) {
        return;
      }

      setQueueState(status);
      if (Date.now() >= status.startsAt) {
        clearQueuePollingTimer();
      }
    };

    const startQueuePolling = (ticketId: string, startsAt: number): void => {
      if (Date.now() >= startsAt || queuePollingTimerRef.current !== null) {
        return;
      }

      queuePollingTimerRef.current = window.setInterval(() => {
        void pollQueueStatus(ticketId);
      }, 1000);
    };

    const joinBackendQueue = async (): Promise<void> => {
      const joined = await joinMatchmakingQueue(loadout.handle);
      if (!joined) {
        return;
      }

      if (queueJoinCancelled) {
        leaveMatchmakingQueue(joined.ticketId);
        return;
      }

      queueTicketId = joined.ticketId;
      setQueueState(joined);
      startQueuePolling(joined.ticketId, joined.startsAt);
    };

    void joinBackendQueue();

    return () => {
      queueJoinCancelled = true;
      clearCountdownTimer();
      clearMatchStartTimer();
      clearQueuePollingTimer();
      if (queueTicketId && !battleStartLockedRef.current) {
        leaveMatchmakingQueue(queueTicketId);
      }
      destroyRuntime();
    };
  }, [matchNonce]);

  const settlementOverlay =
    matchPhase === "settled" && currentResultSummary ? (
      <BattleSettlementOverlay
        summary={currentResultSummary}
        replayId={currentReplayId}
        onNewMatch={() => {
          clearActiveBattleSession();
          setMatchNonce((value) => value + 1);
        }}
      />
    ) : null;

  const drawerOverlay = activeDrawer ? (
    <QuickPreviewOverlay
      {...buildBattleDrawer(activeDrawer, replaySummaries, discussionSummaries, mailSummaries, ratingEntries)}
      onClose={() => setActiveDrawer(null)}
    />
  ) : null;

  return (
    <BattleChrome
      phase={matchPhase}
      leftButtons={QUICK_LEFT.map((item) => ({
        label: item.label,
        iconKey: item.iconKey,
        onClick: () => setActiveDrawer(item.id)
      }))}
      rightButtons={QUICK_RIGHT.map((item) => ({
        label: item.label,
        iconKey: item.iconKey,
        onClick: () => setActiveDrawer(item.id)
      }))}
      matchingOverlay={
        matchPhase === "matching" ? (
          <MatchingOverlay
            countdownMs={matchCountdownMs}
            loadout={loadout}
            presets={presets}
            queueState={queueState}
            onPresetChange={setLoadoutPreset}
          />
        ) : null
      }
      settlementOverlay={settlementOverlay}
      drawerOverlay={drawerOverlay}
    >
      <div ref={runtimeRootRef} className="arena-shell__runtime" aria-label="battle runtime" />
      <div id="hud-root" ref={hudRootRef} className="arena-shell__hud-root" />
    </BattleChrome>
  );
}
import { getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
