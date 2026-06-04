import { useCallback, useRef, type MutableRefObject } from "react";

export interface BattleAuthoritativeBridgeTimers {
  statePollTimerRef: MutableRefObject<number | null>;
  stateStreamCloseRef: MutableRefObject<(() => void) | null>;
  commandUplinkTimerRef: MutableRefObject<number | null>;
  clearAuthoritativeBridgeTimers: () => void;
}

export function useBattleAuthoritativeBridgeTimers(): BattleAuthoritativeBridgeTimers {
  const statePollTimerRef = useRef<number | null>(null);
  const stateStreamCloseRef = useRef<(() => void) | null>(null);
  const commandUplinkTimerRef = useRef<number | null>(null);

  const clearAuthoritativeBridgeTimers = useCallback((): void => {
    if (statePollTimerRef.current !== null) {
      window.clearInterval(statePollTimerRef.current);
      statePollTimerRef.current = null;
    }
    stateStreamCloseRef.current?.();
    stateStreamCloseRef.current = null;
    if (commandUplinkTimerRef.current !== null) {
      window.clearInterval(commandUplinkTimerRef.current);
      commandUplinkTimerRef.current = null;
    }
  }, []);

  return {
    statePollTimerRef,
    stateStreamCloseRef,
    commandUplinkTimerRef,
    clearAuthoritativeBridgeTimers
  };
}
