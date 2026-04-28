import { useSyncExternalStore } from "react";
import { getCurrentAuthUser, subscribeAuthState } from "../../auth/authGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../forum/forumGateway";
import {
  getLoadoutPresets,
  getLoadoutStateVersion,
  getLoadoutSummary,
  setLoadoutPreset,
  subscribeLoadoutState
} from "../../loadout/loadoutGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  REMOTE_MAIL_REFRESH_INTERVAL_MS
} from "../../mails/mailsGateway";
import { getRatingEntries, loadRatingEntries } from "../../rating/ratingGateway";
import { getReplaySummaries, loadReplaySummaries } from "../../replay/replayGateway";
import { useLobbyData } from "../../../shared/ui/useLobbyData";
import type { MatchPhase } from "./battlePageTypes";

interface UseBattlePageDataOptions {
  matchPhase: MatchPhase;
  matchNonce: number;
}

export function useBattlePageData({ matchPhase, matchNonce }: UseBattlePageDataOptions) {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  useSyncExternalStore(subscribeLoadoutState, getLoadoutStateVersion, getLoadoutStateVersion);
  const loadout = getLoadoutSummary();
  const presets = getLoadoutPresets();
  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const mailOwnerHandle = currentUser?.handle;
  const shouldRefreshRemoteMail = isRemoteMailSourceConfigured() && Boolean(mailOwnerHandle?.trim());
  const mailSummaries = useLobbyData(
    () => getMailSummaries(mailOwnerHandle),
    () => loadMergedMailSummaries(mailOwnerHandle),
    [mailOwnerHandle],
    {
      refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: shouldRefreshRemoteMail,
      refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT]
    }
  );
  const unreadMailCount = mailSummaries.filter((mail) => mail.unread).length;
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [matchPhase, matchNonce]);

  return {
    currentUser,
    loadout,
    presets,
    replaySummaries,
    discussionSummaries,
    mailSummaries,
    ratingEntries,
    unreadMailCount,
    onPresetChange: setLoadoutPreset
  };
}
