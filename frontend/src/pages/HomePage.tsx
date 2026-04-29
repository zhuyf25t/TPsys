import { useState, useSyncExternalStore } from "react";
import {
  getCurrentAuthUser,
  logoutLocalUser,
  subscribeAuthState
} from "../features/auth/authGateway";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../features/battle/rules/battleRules";
import {
  getContributionEntries,
  isRemoteContributionSourceConfigured,
  REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS,
  loadContributionEntries
} from "../features/contribution/contributionGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../features/forum/forumGateway";
import { getLoadoutSummary } from "../features/loadout/loadoutGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  REMOTE_MAIL_REFRESH_INTERVAL_MS,
  markMailAsReadRemote
} from "../features/mails/mailsGateway";
import { getProfileSummary, loadProfileSummary } from "../features/profile/profileGateway";
import {
  getRatingEntries,
  isRemoteRatingSourceConfigured,
  loadRatingEntries,
  REMOTE_RATING_REFRESH_INTERVAL_MS
} from "../features/rating/ratingGateway";
import { getReplaySummaries, loadReplaySummaries } from "../features/replay/replayGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS
} from "../features/social/friendRequestGateway";
import {
  buildFriendRequestPreview,
  type FriendRequestPreviewModel
} from "../features/social/friendRequestPreviewPresenter";
import { AuthOverlay } from "../shared/ui/AuthOverlay";
import {
  LobbyShell,
  type LobbyPreviewSet,
  type LobbyQuickAction,
  type LobbyQuickKey
} from "../shared/ui/LobbyShell";
import { useLobbyData } from "../shared/ui/useLobbyData";

const quickActions: LobbyQuickAction[] = [
  { key: "replay", label: "回放", iconKey: "replay", anchor: "left" },
  { key: "discussion", label: "论坛", iconKey: "discussion", anchor: "left" },
  { key: "ranking", label: "排行", iconKey: "ranking", anchor: "left" },
  { key: "mails", label: "邮件", iconKey: "mails", anchor: "right" },
  { key: "social", label: "好友", iconKey: "social", anchor: "right" }
];

export function HomePage() {
  const [authMode, setAuthMode] = useState<"login" | "register" | null>(null);
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const authRefreshKey = authUser ? `${authUser.handle}:${authUser.sessionToken ?? ""}:${authUser.createdAt}` : "guest";

  const loadout = getLoadoutSummary();
  const resolvedHandle = authUser?.handle?.trim() ?? "";
  const remoteMailSource = isRemoteMailSourceConfigured();
  const mailOwnerHandle = authUser?.handle;
  const shouldRefreshRemoteMail = remoteMailSource && Boolean(mailOwnerHandle?.trim());
  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const mailSummaries = useLobbyData(
    () => getMailSummaries(mailOwnerHandle),
    () => loadMergedMailSummaries(mailOwnerHandle),
    [mailOwnerHandle, authRefreshKey],
    {
      refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: shouldRefreshRemoteMail,
      refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT]
    }
  );
  const unreadMailCount = mailSummaries.filter((mail) => mail.unread).length;
  const friendRequestOwnerHandle = authUser?.handle;
  const friendRequests = useLobbyData(
    () => getCachedFriendRequests(friendRequestOwnerHandle),
    () => loadRemoteFriendRequests(friendRequestOwnerHandle),
    [friendRequestOwnerHandle, authRefreshKey],
    {
      enabled: Boolean(friendRequestOwnerHandle?.trim()),
      refreshIntervalMs: friendRequestOwnerHandle?.trim() ? REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: Boolean(friendRequestOwnerHandle?.trim()),
      refreshEvents: [FRIEND_REQUESTS_CHANGED_EVENT]
    }
  );
  const friendRequestPreview = buildFriendRequestPreview(friendRequests, friendRequestOwnerHandle);
  const quickActionsWithBadges = quickActions.map((action) =>
    action.key === "mails"
      ? { ...action, badgeCount: unreadMailCount }
      : action.key === "social"
        ? { ...action, badgeCount: friendRequestPreview.badgeCount }
        : action
  );
  const remoteRatingSource = isRemoteRatingSourceConfigured();
  const remoteContributionSource = isRemoteContributionSourceConfigured();
  const contributionEntries = useLobbyData(
    () => getContributionEntries(),
    loadContributionEntries,
    [resolvedHandle, authRefreshKey],
    {
      refreshIntervalMs: remoteContributionSource ? REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: remoteContributionSource
    }
  );
  const profile = useLobbyData(() => getProfileSummary(resolvedHandle), () => loadProfileSummary(resolvedHandle), [
    resolvedHandle,
    authRefreshKey
  ], {
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
  const leaderboardRating = resolvedHandle
    ? ratingEntries.find((entry) => entry.handle.toLowerCase() === resolvedHandle.toLowerCase())?.score
    : undefined;
  const currentRating = remoteRatingSource
    ? (leaderboardRating ?? profile?.score ?? null)
    : (profile?.score ?? leaderboardRating ?? loadout.rating ?? 1200);
  const currentRatingLabel = currentRating === null ? "—" : String(currentRating);
  const homeMenuIntelCards = [
    {
      eyebrow: "战斗模式",
      value: "3v3",
      detail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人竞技场 / ${BATTLE_MATCH_DURATION_LABEL}`
    },
    {
      eyebrow: "同步协议",
      value: "权威同步",
      detail: "输入校验 · 状态回滚"
    },
    {
      eyebrow: "战报链路",
      value: "回放入库",
      detail: "完赛后生成本地战报"
    },
    {
      eyebrow: "赛季系统",
      value: "评分结算",
      detail: `当前评级 ${currentRatingLabel} · 赛季榜`
    }
  ];

  const playerName = authUser ? resolvedHandle : "访客";

  return (
    <>
      <LobbyShell
        brand="OMEGALOMANIA"
        title="OMEGALOMANIA"
        subtitle={`快节奏 3v3 竞技场 · ${BATTLE_ARENA_PLAYER_CAPACITY} 人钢铁大厅待命`}
        playerName={playerName}
        playerBadge={authUser ? buildHandleBadge(resolvedHandle) : "P1"}
        playerAvatarSrc={loadout.skinImageSrc}
        playerMeta={authUser ? "已登录" : "未登录"}
        playerRating={currentRatingLabel}
        currentLoadoutLabel={`${loadout.primary} · ${loadout.skinLabel}`}
        skillTags={loadout.skills}
        quickActions={quickActionsWithBadges}
        previewSets={buildPreviewSets(
          replaySummaries,
          discussionSummaries,
          mailSummaries,
          ratingEntries,
          friendRequestPreview,
          mailOwnerHandle
        )}
        primaryAction={{ label: "开始游戏", to: "/battle?new=1", variant: "primary" }}
        secondaryAction={{ label: "调整配装", to: "/loadout" }}
        tertiaryAction={
          authUser
            ? {
                label: "退出",
                onClick: () => {
                  logoutLocalUser();
                },
                variant: "ghost"
              }
            : { label: "登录", onClick: () => setAuthMode("login"), variant: "ghost" }
        }
        railItems={[
          { label: "竞技场", value: `${BATTLE_ARENA_PLAYER_CAPACITY} 人` },
          { label: "回合", value: BATTLE_MATCH_DURATION_LABEL },
          { label: "评级", value: currentRatingLabel }
        ]}
        leftDock={<ContributionTopCard entries={contributionEntries} />}
        rightDock={<RatingTopCard entries={ratingEntries} />}
        menuBody={
          <div className="home-menu">
            <div className="home-menu__brandplate">
              <span className="home-menu__kicker">SLAY DEMO / 钢铁战备大厅</span>
              <div className="home-menu__logo" aria-hidden="true">
                OMEGA<span>LOMANIA</span>
              </div>
              <p>快节奏 3v3 竞技场 · 武装同步完成，等待投放</p>
            </div>
            <div className="home-menu__kit" aria-label="当前战备">
              <span>主武器：{loadout.primary}</span>
              <span>战术模块：{loadout.skills.join(" / ")}</span>
            </div>
            <div className="home-menu__intel-grid" aria-label="大厅战情摘要">
              {homeMenuIntelCards.map((card) => (
                <article key={card.eyebrow} className="home-menu__intel-card">
                  <small>{card.eyebrow}</small>
                  <strong>{card.value}</strong>
                  <span>{card.detail}</span>
                </article>
              ))}
            </div>
            <div className="home-menu__status-strip" aria-hidden="true">
              <span>核心在线</span>
              <span>装甲锁定</span>
              <span>投放就绪</span>
            </div>
            {!authUser ? (
              <button type="button" className="home-menu__register" onClick={() => setAuthMode("register")}>
                创建指挥员
              </button>
            ) : null}
          </div>
        }
      />

      {authMode ? (
        <AuthOverlay
          initialMode={authMode}
          onClose={() => setAuthMode(null)}
          onSuccess={() => {
            setAuthMode(null);
          }}
        />
      ) : null}
    </>
  );
}

function ContributionTopCard({
  entries
}: {
  entries: ReturnType<typeof getContributionEntries>;
}) {
  const rows = Array.from({ length: 10 }, (_, index) => entries[index] ?? null);

  return (
    <aside className="lobby-side-card lobby-side-card--contribution" aria-label="贡献榜">
      <header>
        <small>贡献 Top 10</small>
        <strong>贡献榜</strong>
      </header>
      <div className="lobby-side-card__list">
        <div className="lobby-side-card__table-head">
          <span>#</span>
          <span>指挥员</span>
          <span>贡献</span>
        </div>
        {rows.map((row, index) =>
          row ? (
            <article key={row.handle}>
              <span>{row.rank}</span>
              <strong>{row.handle}</strong>
              <b>{row.totalActions}</b>
            </article>
          ) : (
            <article key={index} className="lobby-side-card__empty">
              <span aria-hidden="true" />
              <strong>暂无记录</strong>
              <b>—</b>
            </article>
          )
        )}
      </div>
    </aside>
  );
}

function RatingTopCard({ entries }: { entries: ReturnType<typeof getRatingEntries> }) {
  const rows = Array.from({ length: 10 }, (_, index) => entries[index] ?? null);

  return (
    <aside className="lobby-side-card lobby-side-card--rating" aria-label="评分榜">
      <header>
        <small>评分 Top 10</small>
        <strong>评分榜</strong>
      </header>
      <div className="lobby-side-card__list">
        <div className="lobby-side-card__table-head">
          <span>#</span>
          <span>指挥员</span>
          <span>评级</span>
        </div>
        {rows.map((entry, index) =>
          entry ? (
            <article key={entry.handle}>
              <span>{entry.rank}</span>
              <strong>{entry.handle}</strong>
              <b>{entry.score}</b>
            </article>
          ) : (
            <article key={index} className="lobby-side-card__empty">
              <span aria-hidden="true" />
              <strong>暂无记录</strong>
              <b>—</b>
            </article>
          )
        )}
      </div>
    </aside>
  );
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
        meta: `${replay.resultLabel} · ${replay.finishedAtLabel}`,
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
        meta: `@${topic.author} · ${topic.updatedAt}`,
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
        meta: `${entry.score} · ${entry.title}`,
        detail: `${entry.matchCount} 场 · 胜率 ${entry.winRate}`
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
        meta: `${mail.senderLabel} · ${mail.receivedLabel}`,
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
