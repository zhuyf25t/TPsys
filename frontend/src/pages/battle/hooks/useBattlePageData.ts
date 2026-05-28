import { useSyncExternalStore } from "react";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../../apis/forum/forumGateway";
import {
  getLoadoutPresets,
  getLoadoutStateVersion,
  getLoadoutSummary,
  setLoadoutPreset,
  subscribeLoadoutState
} from "../../../apis/battle/loadoutGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  markMailAsReadRemote,
  REMOTE_MAIL_REFRESH_INTERVAL_MS
} from "../../../apis/mail/mailsGateway";
import { getRatingEntries, loadRatingEntries } from "../../../apis/governance/ratingGateway";
import { getReplaySummaries, loadReplaySummaries } from "../../../apis/replay/replayGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS
} from "../../../apis/social/friendRequestGateway";
import { buildFriendRequestPreview } from "../../friend-requests/components/friendRequestPreviewPresenter";
import { useLobbyData } from "../../shared/hooks/useLobbyData";
import type { MatchPhase } from "./battlePageTypes";

interface UseBattlePageDataOptions {
  matchPhase: MatchPhase;
  matchNonce: number;
}

/** 中文名：使用战斗page数据（useBattlePageData）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
  const friendRequestOwnerHandle = currentUser?.handle;
  const friendRequestAuthKey = currentUser ? `${currentUser.handle}:${currentUser.sessionToken ?? ""}` : "guest";
  const friendRequests = useLobbyData(
    () => getCachedFriendRequests(friendRequestOwnerHandle),
    () => loadRemoteFriendRequests(friendRequestOwnerHandle),
    [friendRequestOwnerHandle, friendRequestAuthKey, matchPhase, matchNonce],
    {
      enabled: Boolean(friendRequestOwnerHandle?.trim()),
      refreshIntervalMs: friendRequestOwnerHandle?.trim() ? REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: Boolean(friendRequestOwnerHandle?.trim()),
      refreshEvents: [FRIEND_REQUESTS_CHANGED_EVENT]
    }
  );
  const friendRequestPreview = buildFriendRequestPreview(friendRequests, friendRequestOwnerHandle);
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [matchPhase, matchNonce]);
  const markDrawerMailRead = (mailId: string): void => {
    if (mailOwnerHandle?.trim()) {
      void markMailAsReadRemote(mailOwnerHandle, mailId);
    }
  };

  return {
    currentUser,
    loadout,
    presets,
    replaySummaries,
    discussionSummaries,
    mailSummaries,
    ratingEntries,
    unreadMailCount,
    friendRequests,
    friendRequestPreview,
    markDrawerMailRead,
    onPresetChange: setLoadoutPreset
  };
}
