import { useCallback, useRef, useState } from "react";
import type { BattlePageTransientNotice } from "../objects/BattlePageState";

const BATTLE_COMMAND_NOTICE_DEDUPE_MS = 1_200;
const BATTLE_COMMAND_NOTICE_VISIBLE_MS = 2_000;

export interface BattleTransientNoticeController {
  transientNotice: BattlePageTransientNotice | null;
  showTransientNotice: (message: string) => void;
  clearTransientNotice: () => void;
}

export function useBattleTransientNotice(): BattleTransientNoticeController {
  const transientNoticeTimerRef = useRef<number | null>(null);
  const transientNoticeLastShownRef = useRef<{ message: string; shownAt: number } | null>(null);
  const transientNoticeIdRef = useRef(0);
  const [transientNotice, setTransientNotice] = useState<BattlePageTransientNotice | null>(null);

  const clearTransientNotice = useCallback((): void => {
    if (transientNoticeTimerRef.current !== null) {
      window.clearTimeout(transientNoticeTimerRef.current);
      transientNoticeTimerRef.current = null;
    }
    transientNoticeLastShownRef.current = null;
    setTransientNotice(null);
  }, []);

  const showTransientNotice = useCallback((message: string): void => {
    const now = performance.now();
    const lastShown = transientNoticeLastShownRef.current;
    if (lastShown?.message === message && now - lastShown.shownAt < BATTLE_COMMAND_NOTICE_DEDUPE_MS) {
      return;
    }

    transientNoticeLastShownRef.current = { message, shownAt: now };
    transientNoticeIdRef.current += 1;
    setTransientNotice({ id: transientNoticeIdRef.current, message });
    if (transientNoticeTimerRef.current !== null) {
      window.clearTimeout(transientNoticeTimerRef.current);
    }
    transientNoticeTimerRef.current = window.setTimeout(() => {
      transientNoticeTimerRef.current = null;
      setTransientNotice(null);
    }, BATTLE_COMMAND_NOTICE_VISIBLE_MS);
  }, []);

  return {
    transientNotice,
    showTransientNotice,
    clearTransientNotice
  };
}
