import { useRef } from "react";

interface TimerRef {
  current: number | null;
}

export function useBattlePageTimers() {
  const countdownStartedAtRef = useRef<number | null>(null);
  const matchWaitDeadlineRef = useRef<number | null>(null);
  const battleDurationDeadlineRef = useRef<number | null>(null);
  const countdownTimerRef = useRef<number | null>(null);
  const matchStartTimerRef = useRef<number | null>(null);
  const queuePollingTimerRef = useRef<number | null>(null);
  const roomPresenceTimerRef = useRef<number | null>(null);
  const snapshotTimerRef = useRef<number | null>(null);
  const battleEndTimerRef = useRef<number | null>(null);
  const battleDurationTimerRef = useRef<number | null>(null);

  return {
    countdownStartedAtRef,
    matchWaitDeadlineRef,
    battleDurationDeadlineRef,
    countdownTimerRef,
    matchStartTimerRef,
    queuePollingTimerRef,
    roomPresenceTimerRef,
    snapshotTimerRef,
    battleEndTimerRef,
    battleDurationTimerRef,
    clearCountdownTimer: () => clearIntervalRef(countdownTimerRef),
    clearMatchStartTimer: () => clearTimeoutRef(matchStartTimerRef),
    clearQueuePollingTimer: () => clearIntervalRef(queuePollingTimerRef),
    clearRoomPresenceTimer: () => clearIntervalRef(roomPresenceTimerRef),
    clearSnapshotTimer: () => clearIntervalRef(snapshotTimerRef),
    clearBattleEndTimer: () => clearIntervalRef(battleEndTimerRef),
    clearBattleDurationTimer: () => clearTimeoutRef(battleDurationTimerRef)
  };
}

function clearIntervalRef(timerRef: TimerRef): void {
  if (timerRef.current !== null) {
    clearInterval(timerRef.current);
    timerRef.current = null;
  }
}

function clearTimeoutRef(timerRef: TimerRef): void {
  if (timerRef.current !== null) {
    clearTimeout(timerRef.current);
    timerRef.current = null;
  }
}
