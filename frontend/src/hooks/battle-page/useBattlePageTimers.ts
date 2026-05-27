import { useRef } from "react";

interface TimerRef {
  current: number | null;
}

/** 中文名：使用战斗pagetimers（useBattlePageTimers）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
