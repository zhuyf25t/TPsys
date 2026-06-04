import { useLocation } from "react-router-dom";

interface BattlePageUrlIntentRefs {
  readonly lastUrlRequestedNewBattleRef: {
    current: boolean;
  };
  readonly newBattleResetPendingRef: {
    current: boolean;
  };
}

interface BattlePageUrlIntent {
  readonly requestsNewBattle: boolean;
  readonly requestsResumeBattle: boolean;
}

export function useBattlePageUrlIntent({
  lastUrlRequestedNewBattleRef,
  newBattleResetPendingRef
}: BattlePageUrlIntentRefs): BattlePageUrlIntent {
  const location = useLocation();
  const urlSearchParams = new URLSearchParams(location.search);
  const requestsNewBattle = urlSearchParams.get("new") === "1";
  const requestsResumeBattle = urlSearchParams.get("resume") === "1" && !requestsNewBattle;

  if (requestsNewBattle && !lastUrlRequestedNewBattleRef.current) {
    newBattleResetPendingRef.current = true;
  }
  lastUrlRequestedNewBattleRef.current = requestsNewBattle;

  return { requestsNewBattle, requestsResumeBattle };
}
