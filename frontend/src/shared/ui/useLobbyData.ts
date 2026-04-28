import { useEffect, useState } from "react";

type LobbyDataLoader<T> = () => Promise<T | null | undefined>;

interface LobbyDataRefreshOptions {
  enabled?: boolean;
  refreshIntervalMs?: number;
  refreshOnFocus?: boolean;
  refreshEvents?: readonly string[];
}

export function useLobbyData<T>(
  getInitialValue: () => T,
  loadValue: LobbyDataLoader<T>,
  deps: readonly unknown[],
  options: LobbyDataRefreshOptions = {}
): T {
  const [value, setValue] = useState(() => getInitialValue());
  const enabled = options.enabled ?? true;
  const refreshIntervalMs = options.refreshIntervalMs ?? 0;
  const refreshOnFocus = options.refreshOnFocus ?? false;
  const refreshEvents = options.refreshEvents ?? [];
  const refreshEventsKey = refreshEvents.join("\u0000");

  useEffect(() => {
    if (!enabled) {
      setValue(getInitialValue());
      return;
    }

    let cancelled = false;
    let loading = false;

    setValue(getInitialValue());

    const refresh = (): void => {
      if (loading) {
        return;
      }

      loading = true;
      void loadValue()
        .then((nextValue) => {
          if (cancelled || nextValue == null) {
            return;
          }

          setValue(nextValue);
        })
        .finally(() => {
          loading = false;
        });
    };

    refresh();

    const handleFocus = (): void => {
      refresh();
    };
    const handleVisibilityChange = (): void => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    };
    const handleRefreshEvent = (): void => {
      setValue(getInitialValue());
      refresh();
    };

    const intervalId = refreshIntervalMs > 0 ? window.setInterval(refresh, refreshIntervalMs) : null;
    if (refreshOnFocus) {
      window.addEventListener("focus", handleFocus);
      document.addEventListener("visibilitychange", handleVisibilityChange);
    }
    refreshEvents.forEach((eventName) => window.addEventListener(eventName, handleRefreshEvent));

    return () => {
      cancelled = true;
      if (intervalId !== null) {
        window.clearInterval(intervalId);
      }
      if (refreshOnFocus) {
        window.removeEventListener("focus", handleFocus);
        document.removeEventListener("visibilitychange", handleVisibilityChange);
      }
      refreshEvents.forEach((eventName) => window.removeEventListener(eventName, handleRefreshEvent));
    };
  }, [...deps, enabled, refreshIntervalMs, refreshOnFocus, refreshEventsKey]);

  return value;
}
