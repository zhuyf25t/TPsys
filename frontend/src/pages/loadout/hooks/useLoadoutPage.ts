import { useState, useSyncExternalStore } from "react";
import {
  getLoadoutSkillOptions,
  getLoadoutStateVersion,
  getLoadoutSummary,
  getSelectedSkillSlots,
  setLoadoutSkin,
  setSkillSlot,
  subscribeLoadoutState,
  swapSkillSlots,
  type LoadoutSkillId,
  type SkillSlotKey
} from "../../../runtime/battle/loadout/BattleLoadoutStore";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../../apis/forum/forumGateway";
import { getRatingEntries, loadRatingEntries } from "../../../apis/governance/ratingGateway";
import { getAuthSkinOptions, getCurrentAuthUser, logoutLocalUser, subscribeAuthState } from "../../../apis/identity/authGateway";
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
import { buildFriendRequestPreview, type FriendRequestPreviewModel } from "../../friend-requests/components/friendRequestPreviewPresenter";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../../../objects/battle/objects/core/BattleCoreRules";
import type { LobbyPreviewSet, LobbyQuickAction, LobbyQuickKey, LobbyShellProps } from "../../../components/ui/LobbyShell";
import { useLobbyData } from "../../shared/hooks/useLobbyData";

export type { LoadoutSkillId, SkillSlotKey } from "../../../runtime/battle/loadout/BattleLoadoutStore";

export type LoadoutTone = "cyan" | "gold" | "ice";
export type LoadoutAuthMode = "login" | "register" | null;
export type LoadoutSummaryView = ReturnType<typeof getLoadoutSummary>;
export type LoadoutSkillSlotView = ReturnType<typeof getSelectedSkillSlots>[number];
export type LoadoutSkillOptionView = ReturnType<typeof getLoadoutSkillOptions>[number];
export type LoadoutSkinOptionView = ReturnType<typeof getAuthSkinOptions>[number];

export interface LoadoutPageState {
  armedSlot: SkillSlotKey | null;
  authMode: LoadoutAuthMode;
  battleCapacityLabel: string;
  closeAuthOverlay: () => void;
  completeAuth: () => void;
  currentLoadoutLabel: string;
  focusedSkill: LoadoutSkillOptionView;
  handleSkillClick: (skillId: LoadoutSkillId) => void;
  handleSlotClick: (slotKey: SkillSlotKey) => void;
  isAuthenticated: boolean;
  loadout: LoadoutSummaryView;
  openRegister: () => void;
  previewSets: Record<LobbyQuickKey, LobbyPreviewSet>;
  primaryAction: LobbyShellProps["primaryAction"];
  quickActions: LobbyQuickAction[];
  railItems: NonNullable<LobbyShellProps["railItems"]>;
  roundDurationLabel: string;
  secondaryAction: LobbyShellProps["secondaryAction"];
  selectSkin: (skinId: string) => void;
  selectedSlotBySkillId: Map<LoadoutSkillId, SkillSlotKey>;
  skillOptions: LoadoutSkillOptionView[];
  skillSlots: LoadoutSkillSlotView[];
  selectedSkin: LoadoutSkinOptionView;
  skinOptions: LoadoutSkinOptionView[];
  tertiaryAction: NonNullable<LobbyShellProps["tertiaryAction"]>;
  unassignedSkillOptions: LoadoutSkillOptionView[];
}

/** 中文名称：配装页Hook。游戏职责：封装战前技能、皮肤和大厅预览状态。 */
export function useLoadoutPage(): LoadoutPageState {
  const [authMode, setAuthMode] = useState<LoadoutAuthMode>(null);
  const [armedSlot, setArmedSlot] = useState<SkillSlotKey | null>(null);
  const [focusedSkillId, setFocusedSkillId] = useState<LoadoutSkillId>("Dash");

  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  useSyncExternalStore(subscribeLoadoutState, getLoadoutStateVersion, getLoadoutStateVersion);
  const loadout = getLoadoutSummary();
  const skillSlots = getSelectedSkillSlots();
  const skillOptions = getLoadoutSkillOptions();
  const skinOptions = getAuthSkinOptions();
  const focusedSkill = skillOptions.find((skill) => skill.id === focusedSkillId) ?? skillOptions[0]!;
  const selectedSkin = skinOptions.find((skin) => skin.id === loadout.skinId) ?? skinOptions[0]!;
  const selectedSkillIds = new Set<LoadoutSkillId>(skillSlots.map((slot) => slot.skillId));
  const selectedSlotBySkillId = new Map<LoadoutSkillId, SkillSlotKey>(skillSlots.map((slot) => [slot.skillId, slot.key]));
  const unassignedSkillOptions = skillOptions.filter((skill) => !selectedSkillIds.has(skill.id));

  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const shouldRefreshRemoteMail = isRemoteMailSourceConfigured() && Boolean(authUser?.handle?.trim());
  const mailSummaries = useLobbyData(() => getMailSummaries(), () => loadMergedMailSummaries(authUser?.handle), [authUser?.handle], {
    refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT],
    refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteMail
  });
  const friendRequestOwnerHandle = authUser?.handle;
  const friendRequestAuthKey = authUser ? `${authUser.handle}:${authUser.sessionToken ?? ""}` : "guest";
  const friendRequests = useLobbyData(
    () => getCachedFriendRequests(friendRequestOwnerHandle),
    () => loadRemoteFriendRequests(friendRequestOwnerHandle),
    [friendRequestOwnerHandle, friendRequestAuthKey],
    {
      enabled: Boolean(friendRequestOwnerHandle?.trim()),
      refreshEvents: [FRIEND_REQUESTS_CHANGED_EVENT],
      refreshIntervalMs: friendRequestOwnerHandle?.trim() ? REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: Boolean(friendRequestOwnerHandle?.trim())
    }
  );
  const friendRequestPreview = buildFriendRequestPreview(friendRequests, friendRequestOwnerHandle);
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [authUser?.handle]);

  function handleSlotClick(slotKey: SkillSlotKey): void {
    const slot = skillSlots.find((candidate) => candidate.key === slotKey);
    if (slot) {
      setFocusedSkillId(slot.skillId);
    }

    if (!armedSlot) {
      setArmedSlot(slotKey);
      return;
    }

    if (armedSlot === slotKey) {
      setArmedSlot(null);
      return;
    }

    swapSkillSlots(armedSlot, slotKey);
    setArmedSlot(null);
  }

  function handleSkillClick(skillId: LoadoutSkillId): void {
    setFocusedSkillId(skillId);

    if (!armedSlot) {
      const owningSlot = skillSlots.find((slot) => slot.skillId === skillId);
      if (owningSlot) {
        setArmedSlot(owningSlot.key);
      }
      return;
    }

    setSkillSlot(armedSlot, skillId);
    setArmedSlot(null);
  }

  const openLogin = (): void => setAuthMode("login");
  const openRegister = (): void => setAuthMode("register");
  const closeAuthOverlay = (): void => setAuthMode(null);

  const primaryAction: LobbyShellProps["primaryAction"] = authUser
    ? { label: "开始", to: "/battle?new=1", variant: "primary" }
    : { label: "登录", onClick: openLogin, variant: "primary" };
  const secondaryAction: LobbyShellProps["secondaryAction"] = { label: "返回大厅", to: "/" };
  const tertiaryAction: NonNullable<LobbyShellProps["tertiaryAction"]> = authUser
    ? {
        label: "退出",
        onClick: () => {
          logoutLocalUser();
        },
        variant: "ghost"
      }
    : { label: "创建档案", onClick: openRegister, variant: "ghost" };

  return {
    armedSlot,
    authMode,
    battleCapacityLabel: `${BATTLE_ARENA_PLAYER_CAPACITY} 人`,
    closeAuthOverlay,
    completeAuth: closeAuthOverlay,
    currentLoadoutLabel: `${skillSlots.map((slot) => slot.label).join(" / ")} | ${loadout.skinLabel}`,
    focusedSkill,
    handleSkillClick,
    handleSlotClick,
    isAuthenticated: Boolean(authUser),
    loadout,
    openRegister,
    previewSets: buildPreviewSets(replaySummaries, discussionSummaries, mailSummaries, ratingEntries, friendRequestPreview, authUser?.handle),
    primaryAction,
    quickActions: [],
    railItems: [
      { label: "ARENA", value: `${BATTLE_ARENA_PLAYER_CAPACITY} 人` },
      { label: "ROUND", value: BATTLE_MATCH_DURATION_LABEL },
      { label: "RULE", value: "单命" }
    ],
    roundDurationLabel: BATTLE_MATCH_DURATION_LABEL,
    secondaryAction,
    selectSkin: setLoadoutSkin,
    selectedSlotBySkillId,
    selectedSkin,
    skillOptions,
    skillSlots,
    skinOptions,
    tertiaryAction,
    unassignedSkillOptions
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
      title: "最近战报",
      eyebrow: "Replay",
      detail: "只显示真实对局记录。",
      emptyTitle: "暂无回放",
      emptyDetail: "完成一局后，这里会出现战报。",
      viewAllPath: "/replay",
      anchor: "left",
      items: replaySummaries.slice(0, 3).map((replay) => ({
        title: replay.title,
        meta: `${replay.resultLabel} | ${replay.finishedAtLabel}`,
        detail: replay.highlightLine
      }))
    },
    discussion: {
      title: "论坛",
      eyebrow: "论坛",
      detail: "战术讨论和赛后复盘。",
      emptyTitle: "暂无讨论",
      emptyDetail: "没有真实帖子时保持空状态。",
      viewAllPath: "/discussion",
      anchor: "left",
      items: discussionSummaries.slice(0, 3).map((topic) => ({
        title: topic.title,
        meta: `@${topic.author} | ${topic.updatedAt}`,
        detail: topic.excerpt
      }))
    },
    ranking: {
      title: "排行预览",
      eyebrow: "Ranking",
      detail: "来自真实结算记录。",
      emptyTitle: "暂无排行",
      emptyDetail: "完成几局后，这里才会出现评分。",
      viewAllPath: "/rating",
      anchor: "left",
      items: ratingEntries.slice(0, 3).map((entry) => ({
        title: `#${entry.rank} ${entry.handle}`,
        meta: `${entry.score} | ${entry.title}`,
        detail: `${entry.matchCount} 场 | 胜率 ${entry.winRate}`
      }))
    },
    mails: {
      title: "最新通知",
      eyebrow: "站内信",
      detail: "战后结算和系统通知。",
      emptyTitle: "暂无邮件",
      emptyDetail: "完成一局后，这里会出现新通知。",
      viewAllPath: "/mails",
      anchor: "right",
      items: mailSummaries.slice(0, 3).map((mail) => ({
        title: mail.subject,
        meta: `${mail.senderLabel} | ${mail.receivedLabel}`,
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
      eyebrow: "Social",
      detail: friendRequestPreview.detail,
      emptyTitle: friendRequestPreview.emptyTitle,
      emptyDetail: friendRequestPreview.emptyDetail,
      viewAllPath: "/mails",
      anchor: "right",
      items: friendRequestPreview.items
    }
  };
}
