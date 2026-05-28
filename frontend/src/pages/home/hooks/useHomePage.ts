import { useState, useSyncExternalStore } from "react";
import { getLoadoutSummary } from "../../../apis/battle/loadoutGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../../apis/forum/forumGateway";
import {
  getContributionEntries,
  isRemoteContributionSourceConfigured,
  loadContributionEntries,
  REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS
} from "../../../apis/governance/contributionGateway";
import {
  getRatingEntries,
  isRemoteRatingSourceConfigured,
  loadRatingEntries,
  REMOTE_RATING_REFRESH_INTERVAL_MS
} from "../../../apis/governance/ratingGateway";
import { getCurrentAuthUser, logoutLocalUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import { getProfileSummary, loadProfileSummary } from "../../../apis/identity/profileGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  markMailAsReadRemote,
  REMOTE_MAIL_REFRESH_INTERVAL_MS
} from "../../../apis/mail/mailsGateway";
import { getReplaySummaries, loadReplaySummaries } from "../../../apis/replay/replayGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS
} from "../../../apis/social/friendRequestGateway";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../../../objects/battle/battleRules";
import { buildFriendRequestPreview, type FriendRequestPreviewModel } from "../../friend-requests/components/friendRequestPreviewPresenter";
import type { LobbyPreviewSet, LobbyQuickAction, LobbyQuickKey, LobbyShellProps, LobbyTopStatusItem } from "../../../components/ui/LobbyShell";
import { useLobbyData } from "../../shared/hooks/useLobbyData";

export type HomeAuthMode = "login" | "register" | null;

export interface HomeLeaderboardEntry {
  handle: string;
  rank: number;
  value: number;
}

export interface HomePageState {
  authMode: HomeAuthMode;
  battleModeDetail: string;
  closeAuthOverlay: () => void;
  completeAuth: () => void;
  contributionLeaderboard: HomeLeaderboardEntry[];
  currentLoadoutLabel: string;
  currentRatingLabel: string;
  loadoutPrimary: string;
  loadoutSkillsLabel: string;
  lobbySubtitle: string;
  openRegister: () => void;
  playerAvatarSrc: string;
  playerBadge: string;
  playerMeta: string;
  playerName: string;
  previewSets: Record<LobbyQuickKey, LobbyPreviewSet>;
  primaryAction: LobbyShellProps["primaryAction"];
  quickActions: LobbyQuickAction[];
  railItems: NonNullable<LobbyShellProps["railItems"]>;
  ratingLeaderboard: HomeLeaderboardEntry[];
  recentReplayCount: number;
  replayTotalCount: number;
  secondaryAction: LobbyShellProps["secondaryAction"];
  skillTags: string[];
  syncDetail: string;
  tertiaryAction: NonNullable<LobbyShellProps["tertiaryAction"]>;
  topStatusItems: LobbyTopStatusItem[];
}

const quickActions: LobbyQuickAction[] = [
  { key: "replay", label: "回放", iconKey: "replay", anchor: "left" },
  { key: "discussion", label: "论坛", iconKey: "discussion", anchor: "left" },
  { key: "ranking", label: "排行", iconKey: "ranking", anchor: "left" },
  { key: "mails", label: "邮件", iconKey: "mails", anchor: "right" },
  { key: "social", label: "好友", iconKey: "social", anchor: "right" }
];

/** 中文名称：首页Hook。游戏职责：封装大厅认证、数据预览和排行榜聚合。 */
export function useHomePage(): HomePageState {
  const [authMode, setAuthMode] = useState<HomeAuthMode>(null);
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const authRefreshKey = authUser ? `${authUser.handle}:${authUser.sessionToken ?? ""}:${authUser.createdAt}` : "guest";

  const loadout = getLoadoutSummary();
  const resolvedHandle = authUser?.handle?.trim() ?? "";
  const mailOwnerHandle = authUser?.handle;
  const shouldRefreshRemoteMail = isRemoteMailSourceConfigured() && Boolean(mailOwnerHandle?.trim());
  const remoteRatingSource = isRemoteRatingSourceConfigured();
  const remoteContributionSource = isRemoteContributionSourceConfigured();

  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const mailSummaries = useLobbyData(
    () => getMailSummaries(mailOwnerHandle),
    () => loadMergedMailSummaries(mailOwnerHandle),
    [mailOwnerHandle, authRefreshKey],
    {
      refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT],
      refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: shouldRefreshRemoteMail
    }
  );
  const friendRequestOwnerHandle = authUser?.handle;
  const friendRequests = useLobbyData(
    () => getCachedFriendRequests(friendRequestOwnerHandle),
    () => loadRemoteFriendRequests(friendRequestOwnerHandle),
    [friendRequestOwnerHandle, authRefreshKey],
    {
      enabled: Boolean(friendRequestOwnerHandle?.trim()),
      refreshEvents: [FRIEND_REQUESTS_CHANGED_EVENT],
      refreshIntervalMs: friendRequestOwnerHandle?.trim() ? REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: Boolean(friendRequestOwnerHandle?.trim())
    }
  );
  const contributionEntries = useLobbyData(
    () => getContributionEntries(),
    loadContributionEntries,
    [resolvedHandle, authRefreshKey],
    {
      refreshIntervalMs: remoteContributionSource ? REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: remoteContributionSource
    }
  );
  const profile = useLobbyData(() => getProfileSummary(resolvedHandle), () => loadProfileSummary(resolvedHandle), [resolvedHandle, authRefreshKey], {
    enabled: Boolean(resolvedHandle)
  });
  const ratingEntries = useLobbyData(
    () => getRatingEntries(),
    loadRatingEntries,
    [resolvedHandle, authRefreshKey],
    {
      refreshIntervalMs: remoteRatingSource ? REMOTE_RATING_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: remoteRatingSource
    }
  );

  const unreadMailCount = mailSummaries.filter((mail) => mail.unread).length;
  const friendRequestPreview = buildFriendRequestPreview(friendRequests, friendRequestOwnerHandle);
  const quickActionsWithBadges = quickActions.map((action) =>
    action.key === "mails"
      ? { ...action, badgeCount: unreadMailCount }
      : action.key === "social"
        ? { ...action, badgeCount: friendRequestPreview.badgeCount }
        : action
  );
  const leaderboardRating = resolvedHandle
    ? ratingEntries.find((entry) => entry.handle.toLowerCase() === resolvedHandle.toLowerCase())?.score
    : undefined;
  const currentRating = remoteRatingSource
    ? (leaderboardRating ?? profile?.score ?? null)
    : (profile?.score ?? leaderboardRating ?? loadout.rating ?? 1200);
  const currentRatingLabel = currentRating === null ? "-" : String(currentRating);
  const playerName = authUser ? resolvedHandle : "未登录";
  const recentReplayCount = Math.min(replaySummaries.length, 3);

  const openLogin = (): void => setAuthMode("login");
  const openRegister = (): void => setAuthMode("register");
  const closeAuthOverlay = (): void => setAuthMode(null);

  const primaryAction: LobbyShellProps["primaryAction"] = authUser
    ? { label: "开始游戏", to: "/battle?new=1", variant: "primary" }
    : { label: "登录后开战", onClick: openLogin, variant: "primary" };
  const secondaryAction: LobbyShellProps["secondaryAction"] = { label: "调整配装", to: "/loadout" };
  const tertiaryAction: NonNullable<LobbyShellProps["tertiaryAction"]> = authUser
    ? {
        label: "退出",
        onClick: () => {
          logoutLocalUser();
        },
        variant: "ghost"
      }
    : { label: "登录", onClick: openLogin, variant: "ghost" };

  return {
    authMode,
    battleModeDetail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人竞技场 / ${BATTLE_MATCH_DURATION_LABEL}`,
    closeAuthOverlay,
    completeAuth: closeAuthOverlay,
    contributionLeaderboard: contributionEntries.map((entry) => ({ rank: entry.rank, handle: entry.handle, value: entry.totalActions })),
    currentLoadoutLabel: `${loadout.primary} / ${loadout.skinLabel}`,
    currentRatingLabel,
    loadoutPrimary: loadout.primary,
    loadoutSkillsLabel: loadout.skills.join(" / "),
    lobbySubtitle: `快节奏 3v3 竞技场 / ${BATTLE_ARENA_PLAYER_CAPACITY} 人钢铁大厅待命`,
    openRegister,
    playerAvatarSrc: loadout.skinImageSrc,
    playerBadge: authUser ? buildHandleBadge(resolvedHandle) : "P1",
    playerMeta: authUser ? "已登录" : "未登录",
    playerName,
    previewSets: buildPreviewSets(replaySummaries, discussionSummaries, mailSummaries, ratingEntries, friendRequestPreview, mailOwnerHandle),
    primaryAction,
    quickActions: quickActionsWithBadges,
    railItems: [
      { label: "竞技场", value: `${BATTLE_ARENA_PLAYER_CAPACITY} 人` },
      { label: "回合", value: BATTLE_MATCH_DURATION_LABEL },
      { label: "评级", value: currentRatingLabel }
    ],
    ratingLeaderboard: ratingEntries.map((entry) => ({ rank: entry.rank, handle: entry.handle, value: entry.score })),
    recentReplayCount,
    replayTotalCount: replaySummaries.length,
    secondaryAction,
    skillTags: loadout.skills,
    syncDetail: "输入校验 / 状态回滚",
    tertiaryAction,
    topStatusItems: [
      {
        label: "战区状态",
        value: "竞技场在线",
        detail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人容量 / ${BATTLE_MATCH_DURATION_LABEL}`,
        tone: "ready"
      },
      {
        label: "赛季状态",
        value: currentRatingLabel === "-" ? "等待定级" : `评级 ${currentRatingLabel}`,
        detail: remoteRatingSource ? "远端榜单同步" : "本地评分通道",
        tone: currentRatingLabel === "-" ? "idle" : "data"
      },
      {
        label: "通讯",
        value: authUser ? "账号在线" : "未登录",
        detail: unreadMailCount > 0 ? `${unreadMailCount} 封未读邮件` : "通讯链路清空",
        tone: unreadMailCount > 0 ? "alert" : "ready"
      }
    ]
  };
}

function buildPreviewSets(
  replaySummaries: ReturnType<typeof getReplaySummaries>,
  discussionSummaries: ReturnType<typeof getDiscussionSummaries>,
  mailSummaries: ReturnType<typeof getMailSummaries>,
  ratingEntries: ReturnType<typeof getRatingEntries>,
  friendRequestPreview: FriendRequestPreviewModel,
  mailOwnerHandle?: string | null
): Record<LobbyQuickKey, LobbyPreviewSet> {
  return {
    replay: {
      title: "最近回放",
      eyebrow: "战报回放",
      detail: "只显示已经产生的本地战报。",
      emptyTitle: "暂无回放",
      emptyDetail: "完成一局后，这里会出现真实战报。",
      viewAllPath: "/replay",
      anchor: "left",
      items: replaySummaries.slice(0, 3).map((replay) => ({
        title: replay.title,
        meta: `${replay.resultLabel} / ${replay.finishedAtLabel}`,
        detail: replay.highlightLine
      }))
    },
    discussion: {
      title: "论坛",
      eyebrow: "战术论坛",
      detail: "最近的本地讨论。",
      emptyTitle: "暂无讨论",
      emptyDetail: "还没有帖子。",
      viewAllPath: "/discussion",
      anchor: "left",
      items: discussionSummaries.slice(0, 3).map((topic) => ({
        title: topic.title,
        meta: `@${topic.author} / ${topic.updatedAt}`,
        detail: topic.excerpt
      }))
    },
    ranking: {
      title: "排行",
      eyebrow: "评级排行",
      detail: "当前已有评分记录。",
      emptyTitle: "暂无排行",
      emptyDetail: "完成对局后才会生成排行。",
      viewAllPath: "/rating",
      anchor: "left",
      items: ratingEntries.slice(0, 5).map((entry) => ({
        title: `#${entry.rank} ${entry.handle}`,
        meta: `${entry.score} / ${entry.title}`,
        detail: `${entry.matchCount} 场 / 胜率 ${entry.winRate}`
      }))
    },
    mails: {
      title: "邮件",
      eyebrow: "战备邮件",
      detail: "最近通知。",
      emptyTitle: "暂无邮件",
      emptyDetail: "完成一局后，这里会出现通知。",
      viewAllPath: "/mails",
      anchor: "right",
      items: mailSummaries.slice(0, 3).map((mail) => ({
        title: mail.subject,
        meta: `${mail.senderLabel} / ${mail.receivedLabel}`,
        detail: mail.excerpt,
        onSelect:
          mail.unread && mailOwnerHandle?.trim()
            ? () => {
                void markMailAsReadRemote(mailOwnerHandle, mail.id);
              }
            : undefined
      }))
    },
    social: {
      title: "好友",
      eyebrow: "好友联络",
      detail: friendRequestPreview.detail,
      emptyTitle: friendRequestPreview.emptyTitle,
      emptyDetail: friendRequestPreview.emptyDetail,
      viewAllPath: "/mails",
      anchor: "right",
      items: friendRequestPreview.items
    }
  };
}

function buildHandleBadge(handle: string): string {
  return handle
    .split(/[\s-_]+/)
    .filter(Boolean)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("")
    .slice(0, 2)
    .padEnd(2, "P");
}
