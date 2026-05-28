import { useSyncExternalStore } from "react";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import {
  getRatingEntries,
  isRemoteRatingSourceConfigured,
  loadRatingEntries,
  REMOTE_RATING_REFRESH_INTERVAL_MS,
  type RatingEntry
} from "../../../apis/governance/ratingGateway";
import { useLobbyData } from "../../shared/hooks/useLobbyData";

export interface RatingPageState {
  ratingEntries: RatingEntry[];
}

export function useRatingPage(): RatingPageState {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const shouldRefreshRemoteRating = isRemoteRatingSourceConfigured();
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [authUser?.handle], {
    refreshIntervalMs: shouldRefreshRemoteRating ? REMOTE_RATING_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteRating
  });

  return { ratingEntries };
}
