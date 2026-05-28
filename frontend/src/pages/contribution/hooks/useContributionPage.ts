import { useSyncExternalStore } from "react";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import {
  getContributionEntries,
  isRemoteContributionSourceConfigured,
  loadContributionEntries,
  REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS,
  type ContributionEntry
} from "../../../apis/governance/contributionGateway";
import { CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT } from "../../../apis/governance/governanceGateway";
import { useLobbyData } from "../../shared/hooks/useLobbyData";

export interface ContributionPageState {
  entries: ContributionEntry[];
}

export function useContributionPage(): ContributionPageState {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const shouldRefreshRemoteContribution = isRemoteContributionSourceConfigured();
  const entries = useLobbyData(() => getContributionEntries(), loadContributionEntries, [authUser?.handle], {
    refreshIntervalMs: shouldRefreshRemoteContribution ? REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteContribution,
    refreshEvents: [CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT]
  });

  return { entries };
}
