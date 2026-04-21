import { useEffect, useState } from "react";

type LobbyDataLoader<T> = () => Promise<T | null | undefined>;

export function useLobbyData<T>(
  getInitialValue: () => T,
  loadValue: LobbyDataLoader<T>,
  deps: readonly unknown[]
): T {
  const [value, setValue] = useState(() => getInitialValue());

  useEffect(() => {
    let cancelled = false;

    setValue(getInitialValue());

    void loadValue().then((nextValue) => {
      if (cancelled || nextValue == null) {
        return;
      }

      setValue(nextValue);
    });

    return () => {
      cancelled = true;
    };
  }, deps);

  return value;
}
