import { useState, useSyncExternalStore } from "react";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../../../domains/battle/objects/battleRules";
import { getLoadoutSummary } from "../../../domains/battle/api/loadoutGateway";
import { AuthOverlay } from "../../../domains/identity/components/AuthOverlay";
import { getCurrentAuthUser, logoutLocalUser, subscribeAuthState } from "../../../domains/identity/api/authGateway";
import { getProfileSummary, loadProfileSummary } from "../../../domains/identity/api/profileGateway";
import {
  getContributionEntries,
  isRemoteContributionSourceConfigured,
  loadContributionEntries,
  REMOTE_CONTRIBUTION_REFRESH_INTERVAL_MS
} from "../../../domains/governance/api/contributionGateway";
import {
  getRatingEntries,
  isRemoteRatingSourceConfigured,
  loadRatingEntries,
  REMOTE_RATING_REFRESH_INTERVAL_MS
} from "../../../domains/governance/api/ratingGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../../domains/forum/api/forumGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  markMailAsReadRemote,
  REMOTE_MAIL_REFRESH_INTERVAL_MS
} from "../../../domains/mail/api/mailsGateway";
import { getReplaySummaries, loadReplaySummaries } from "../../../domains/replay/api/replayGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS
} from "../../../domains/social/api/friendRequestGateway";
import {
  buildFriendRequestPreview,
  type FriendRequestPreviewModel
} from "../../../domains/social/components/friend-requests/friendRequestPreviewPresenter";
import {
  LobbyShell,
  type LobbyPreviewSet,
  type LobbyQuickAction,
  type LobbyQuickKey,
  type LobbyTopStatusItem
} from "../../../shared/ui/LobbyShell";
import { cn } from "../../../shared/ui/classNames";
import { useLobbyData } from "../../../shared/ui/useLobbyData";

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
      refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: shouldRefreshRemoteMail,
      refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT]
    }
  );
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
  const topStatusItems: LobbyTopStatusItem[] = [
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
  ];

  return (
    <>
      <LobbyShell
        brand="OMEGALOMANIA"
        title="OMEGALOMANIA"
        subtitle={`快节奏 3v3 竞技场 / ${BATTLE_ARENA_PLAYER_CAPACITY} 人钢铁大厅待命`}
        playerName={playerName}
        playerBadge={authUser ? buildHandleBadge(resolvedHandle) : "P1"}
        playerAvatarSrc={loadout.skinImageSrc}
        playerMeta={authUser ? "已登录" : "未登录"}
        playerRating={currentRatingLabel}
        currentLoadoutLabel={`${loadout.primary} / ${loadout.skinLabel}`}
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
        primaryAction={
          authUser
            ? { label: "开始游戏", to: "/battle?new=1", variant: "primary" }
            : { label: "登录后开战", onClick: () => setAuthMode("login"), variant: "primary" }
        }
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
        topStatusItems={topStatusItems}
        leftDock={<LeaderboardCard title="贡献榜" eyebrow="贡献 Top 10" entries={contributionEntries.map((entry) => ({ rank: entry.rank, handle: entry.handle, value: entry.totalActions }))} />}
        rightDock={<LeaderboardCard title="评分榜" eyebrow="评分 Top 10" entries={ratingEntries.map((entry) => ({ rank: entry.rank, handle: entry.handle, value: entry.score }))} />}
        menuBody={
          <div className="grid gap-4">
            <section className="rounded border border-amber-200/20 bg-amber-300/10 p-4">
              <span className="text-xs font-black uppercase tracking-[0.24em] text-amber-100">SLAY DEMO / 钢铁战备大厅</span>
              <div className="mt-4 grid gap-3 lg:grid-cols-[1fr_auto] lg:items-end">
                <div>
                  <strong className="text-2xl font-black text-white">金属战役中枢</strong>
                  <p className="mt-2 text-sm leading-6 text-slate-300">快节奏 3v3 竞技场。武装同步完成，等待投放。</p>
                </div>
                {!authUser ? (
                  <button
                    type="button"
                    className="rounded border border-amber-200/50 bg-amber-300/20 px-4 py-3 text-sm font-black text-amber-50 transition hover:bg-amber-300/30"
                    onClick={() => setAuthMode("register")}
                  >
                    创建指挥官
                  </button>
                ) : null}
              </div>
            </section>

            <section className="grid gap-3 md:grid-cols-3" aria-label="大厅战情摘要">
              {[
                { label: "战斗模式", value: "3v3", detail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人竞技场 / ${BATTLE_MATCH_DURATION_LABEL}` },
                { label: "同步协议", value: "权威同步", detail: "输入校验 / 状态回滚" },
                { label: "回放链路", value: String(recentReplayCount), detail: replaySummaries.length > 0 ? `战报库 ${replaySummaries.length} 条` : "完赛后生成" }
              ].map((card) => (
                <article key={card.label} className="rounded border border-white/10 bg-white/[0.04] p-3">
                  <small className="text-xs font-bold text-slate-400">{card.label}</small>
                  <strong className="mt-1 block text-lg text-white">{card.value}</strong>
                  <span className="mt-1 block text-xs text-slate-400">{card.detail}</span>
                </article>
              ))}
            </section>

            <div className="flex flex-wrap gap-2 text-xs font-bold text-slate-300" aria-label="当前战备">
              <span className="rounded border border-white/10 bg-white/5 px-3 py-1">主武器：{loadout.primary}</span>
              <span className="rounded border border-white/10 bg-white/5 px-3 py-1">战术模块：{loadout.skills.join(" / ")}</span>
            </div>
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

function LeaderboardCard({
  title,
  eyebrow,
  entries
}: {
  title: string;
  eyebrow: string;
  entries: Array<{ rank: number; handle: string; value: number }>;
}) {
  const rows = Array.from({ length: 10 }, (_, index) => entries[index] ?? null);
  const occupiedSlots = Math.min(entries.length, rows.length);

  return (
    <aside className="rounded border border-white/10 bg-slate-950/75 p-4 text-slate-100 shadow-2xl shadow-black/30 backdrop-blur" aria-label={title}>
      <header className="mb-4 flex items-start justify-between gap-4 border-b border-white/10 pb-3">
        <div>
          <small className="text-xs font-black uppercase tracking-[0.2em] text-cyan-200">{eyebrow}</small>
          <strong className="mt-1 block text-lg text-white">{title}</strong>
        </div>
        <span className="rounded border border-cyan-200/30 bg-cyan-300/10 px-2 py-1 text-xs font-black text-cyan-100">{occupiedSlots}/10</span>
      </header>
      <div className="mb-3 rounded border border-white/10 bg-white/[0.04] p-3">
        <span className="text-xs text-slate-400">真实记录</span>
        <strong className="ml-2 text-lg text-white">{occupiedSlots}</strong>
      </div>
      <div className="grid gap-2">
        <div className="grid grid-cols-[40px_1fr_72px] gap-2 text-xs font-bold text-slate-500">
          <span>#</span>
          <span>指挥官</span>
          <span className="text-right">数值</span>
        </div>
        {rows.map((row, index) =>
          row ? (
            <article
              key={row.handle}
              className={cn(
                "grid grid-cols-[40px_1fr_72px] gap-2 rounded border border-white/10 bg-white/[0.04] px-3 py-2 text-sm",
                row.rank <= 3 && "border-amber-200/30 bg-amber-300/10"
              )}
            >
              <span className="font-black text-amber-100">{row.rank}</span>
              <strong className="truncate text-white">{row.handle}</strong>
              <b className="text-right text-cyan-100">{row.value}</b>
            </article>
          ) : (
            <article key={index} className="grid grid-cols-[40px_1fr_72px] gap-2 rounded border border-white/5 bg-white/[0.02] px-3 py-2 text-sm text-slate-500">
              <span>{index + 1}</span>
              <strong>暂无记录</strong>
              <b className="text-right">-</b>
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
