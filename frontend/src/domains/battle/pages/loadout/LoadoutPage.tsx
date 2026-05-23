import { useState, useSyncExternalStore } from "react";
import { getAuthSkinOptions, getCurrentAuthUser, logoutLocalUser, subscribeAuthState } from "../../../identity/api/authGateway";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../../objects/battleRules";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../../../forum/api/forumGateway";
import {
  getLoadoutPresets,
  getLoadoutSkillOptions,
  getLoadoutStateVersion,
  getLoadoutSummary,
  getSelectedSkillSlots,
  setLoadoutPreset,
  setLoadoutSkin,
  setSkillSlot,
  subscribeLoadoutState,
  swapSkillSlots,
  type LoadoutSkillId,
  type SkillSlotKey
} from "../../api/loadoutGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  markMailAsReadRemote,
  REMOTE_MAIL_REFRESH_INTERVAL_MS
} from "../../../mail/api/mailsGateway";
import { getRatingEntries, loadRatingEntries } from "../../../governance/api/ratingGateway";
import { getReplaySummaries, loadReplaySummaries } from "../../../replay/api/replayGateway";
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  getCachedFriendRequests,
  loadRemoteFriendRequests,
  REMOTE_FRIEND_REQUEST_REFRESH_INTERVAL_MS
} from "../../../social/api/friendRequestGateway";
import {
  buildFriendRequestPreview,
  type FriendRequestPreviewModel
} from "../../../social/components/friend-requests/friendRequestPreviewPresenter";
import { AuthOverlay } from "../../../identity/components/AuthOverlay";
import { LobbyShell, type LobbyPreviewSet, type LobbyQuickAction, type LobbyQuickKey } from "../../../../shared/ui/LobbyShell";
import { useLobbyData } from "../../../../shared/ui/useLobbyData";
import { cn } from "../../../../shared/ui/classNames";

type LoadoutTone = "cyan" | "gold" | "ice";

const quickActions: LobbyQuickAction[] = [
  { key: "replay", label: "回放", iconKey: "replay", anchor: "left" },
  { key: "discussion", label: "论坛", iconKey: "discussion", anchor: "left" },
  { key: "ranking", label: "排行", iconKey: "ranking", anchor: "left" },
  { key: "mails", label: "邮件", iconKey: "mails", anchor: "right" },
  { key: "social", label: "好友", iconKey: "social", anchor: "right" }
];

/** 中文名称：配装页。游戏职责：组织战前技能、武器打法和皮肤选择。 */
export function LoadoutPage() {
  const [authMode, setAuthMode] = useState<"login" | "register" | null>(null);
  const [armedSlot, setArmedSlot] = useState<SkillSlotKey | null>(null);

  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  useSyncExternalStore(subscribeLoadoutState, getLoadoutStateVersion, getLoadoutStateVersion);
  const loadout = getLoadoutSummary();
  const presets = getLoadoutPresets();
  const skillSlots = getSelectedSkillSlots();
  const skillOptions = getLoadoutSkillOptions();
  const skinOptions = getAuthSkinOptions();
  const selectedSkillIds = new Set<LoadoutSkillId>(skillSlots.map((slot) => slot.skillId));
  const selectedSlotBySkillId = new Map<LoadoutSkillId, SkillSlotKey>(skillSlots.map((slot) => [slot.skillId, slot.key]));
  const unassignedSkillOptions = skillOptions.filter((skill) => !selectedSkillIds.has(skill.id));
  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const shouldRefreshRemoteMail = isRemoteMailSourceConfigured() && Boolean(authUser?.handle?.trim());
  const mailSummaries = useLobbyData(() => getMailSummaries(), () => loadMergedMailSummaries(authUser?.handle), [authUser?.handle], {
    refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
    refreshOnFocus: shouldRefreshRemoteMail,
    refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT]
  });
  const unreadMailCount = mailSummaries.filter((mail) => mail.unread).length;
  const friendRequestOwnerHandle = authUser?.handle;
  const friendRequestAuthKey = authUser ? `${authUser.handle}:${authUser.sessionToken ?? ""}` : "guest";
  const friendRequests = useLobbyData(
    () => getCachedFriendRequests(friendRequestOwnerHandle),
    () => loadRemoteFriendRequests(friendRequestOwnerHandle),
    [friendRequestOwnerHandle, friendRequestAuthKey],
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
  const ratingEntries = useLobbyData(() => getRatingEntries(), loadRatingEntries, [authUser?.handle]);

  function handleSlotClick(slotKey: SkillSlotKey): void {
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

  const previewSets = buildPreviewSets(replaySummaries, discussionSummaries, mailSummaries, ratingEntries, friendRequestPreview, authUser?.handle);

  return (
    <>
      <LobbyShell
        layoutMode="solo"
        brand="LOADOUT"
        title="战前配装"
        subtitle="选择 Q / E / R 技能槽、武器打法和皮肤，保持大厅游戏菜单体验。"
        playerName={authUser ? loadout.handle : "访客"}
        playerBadge={authUser ? "P1" : "GUEST"}
        playerAvatarSrc={loadout.skinImageSrc}
        playerMeta={authUser ? "已登录 | 本地配装保存" : "访客模式 | 登录后保存"}
        playerRating={String(loadout.rating)}
        currentLoadoutLabel={`${loadout.presetLabel} | ${loadout.skinLabel}`}
        skillTags={loadout.skills}
        quickActions={quickActionsWithBadges}
        previewSets={previewSets}
        primaryAction={authUser ? { label: "开始", to: "/battle?new=1", variant: "primary" } : { label: "登录", onClick: () => setAuthMode("login"), variant: "primary" }}
        secondaryAction={{ label: "返回大厅", to: "/" }}
        tertiaryAction={
          authUser
            ? {
                label: "退出",
                onClick: () => {
                  logoutLocalUser();
                },
                variant: "ghost"
              }
            : { label: "创建档案", onClick: () => setAuthMode("register"), variant: "ghost" }
        }
        railItems={[
          { label: "ARENA", value: `${BATTLE_ARENA_PLAYER_CAPACITY} 人` },
          { label: "ROUND", value: BATTLE_MATCH_DURATION_LABEL },
          { label: "RULE", value: "单命" }
        ]}
        menuBody={
          <div className="flex flex-col gap-5">
            <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Q / E / R 技能槽</span>
                  <strong className="mt-1 block text-base text-slate-950">
                    {armedSlot ? `正在编辑 ${armedSlot}` : "点击槽位，再点击技能替换；点击已装备技能可定位槽位。"}
                  </strong>
                </div>
                <div className="flex flex-wrap gap-2 text-xs font-semibold text-slate-600" aria-label="配装状态">
                  <span className="rounded-full bg-slate-100 px-3 py-1">已装备 {skillSlots.length}</span>
                  <span className="rounded-full bg-slate-100 px-3 py-1">未装备 {unassignedSkillOptions.length}</span>
                  <span className="rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">{armedSlot ? "选择技能或交换槽位" : "待机"}</span>
                </div>
              </div>
            </div>

            <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <SectionHeader eyebrow="Skill Slots" title="技能键位" />
              <p className="mt-2 text-sm leading-6 text-slate-600">
                {armedSlot ? `当前选中 ${armedSlot} 槽：点技能会替换，点另一个槽位会交换。` : "先点 Q / E / R 槽位，再从技能池选择 Blink / Dash / Freeze。"}
              </p>

              <div className="mt-4 grid gap-3 md:grid-cols-3" aria-label="已选技能槽">
                {skillSlots.map((slot) => (
                  <button
                    key={slot.key}
                    type="button"
                    className={cn(
                      "min-h-32 rounded-lg border p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md",
                      toneSurfaceClass(slot.tone),
                      armedSlot === slot.key ? "ring-2 ring-emerald-500 ring-offset-2" : ""
                    )}
                    aria-pressed={armedSlot === slot.key}
                    onClick={() => handleSlotClick(slot.key)}
                  >
                    <span className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-white/80 text-sm font-bold text-slate-950 shadow-sm">{slot.key}</span>
                    <strong className="mt-3 block text-lg text-slate-950">{slot.label}</strong>
                    <small className="mt-1 block text-sm leading-5 text-slate-600">{slot.description}</small>
                  </button>
                ))}
              </div>

              <div className="mt-5 grid gap-4 lg:grid-cols-[1fr_280px]" aria-label="技能池">
                <div>
                  <small className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Skill Bank</small>
                  <div className="mt-2 grid gap-3 md:grid-cols-3">
                    {skillOptions.map((skill) => {
                      const selectedSlot = selectedSlotBySkillId.get(skill.id);
                      const isSelected = Boolean(selectedSlot);

                      return (
                        <button
                          key={skill.id}
                          type="button"
                          className={cn(
                            "rounded-lg border p-3 text-left transition hover:-translate-y-0.5",
                            isSelected ? toneSurfaceClass(skill.tone) : "border-slate-200 bg-slate-50 text-slate-700",
                            selectedSlot === armedSlot ? "ring-2 ring-emerald-500 ring-offset-2" : "",
                            armedSlot ? "hover:border-emerald-500" : ""
                          )}
                          aria-pressed={isSelected}
                          onClick={() => handleSkillClick(skill.id)}
                        >
                          <span className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">{skill.shortLabel}</span>
                          <strong className="mt-1 block text-base text-slate-950">{skill.label}</strong>
                          <small className="mt-1 block text-xs text-slate-500">{selectedSlot ? `${selectedSlot} 已选中` : "未选中"}</small>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div>
                  <small className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Unselected</small>
                  <div className="mt-2 flex flex-col gap-2">
                    {unassignedSkillOptions.length ? (
                      unassignedSkillOptions.map((skill) => (
                        <button
                          key={skill.id}
                          type="button"
                          className={cn("rounded-lg border p-3 text-left transition hover:-translate-y-0.5", toneSurfaceClass(skill.tone))}
                          onClick={() => handleSkillClick(skill.id)}
                        >
                          <span className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">{skill.shortLabel}</span>
                          <strong className="ml-2 text-sm text-slate-950">{skill.label}</strong>
                        </button>
                      ))
                    ) : (
                      <span className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3 text-sm leading-6 text-slate-500">
                        当前仅开放 Blink / Dash / Freeze，三项均已入槽。
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <SectionHeader eyebrow="Weapon Preset" title="武器打法" />
              <div className="mt-4 grid gap-3 md:grid-cols-3">
                {presets.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    className={cn(
                      "rounded-lg border p-4 text-left transition hover:-translate-y-0.5 hover:shadow-md",
                      preset.id === loadout.presetId ? "border-emerald-500 bg-emerald-50 ring-2 ring-emerald-500/20" : "border-slate-200 bg-white"
                    )}
                    onClick={() => setLoadoutPreset(preset.id)}
                  >
                    <small className="text-xs font-semibold text-slate-500">{preset.label}</small>
                    <strong className="mt-1 block text-base text-slate-950">{preset.primary}</strong>
                    <span className="mt-2 block text-sm leading-6 text-slate-600">{preset.description}</span>
                  </button>
                ))}
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <SectionHeader eyebrow="Skin" title="皮肤" />
              <div className="mt-4 flex flex-wrap gap-3" aria-label="皮肤选择">
                {skinOptions.map((skin) => (
                  <button
                    key={skin.id}
                    type="button"
                    className={cn(
                      "flex min-w-28 items-center gap-3 rounded-lg border p-3 text-left transition hover:-translate-y-0.5 hover:shadow-md",
                      skin.id === loadout.skinId ? "border-emerald-500 bg-emerald-50 ring-2 ring-emerald-500/20" : "border-slate-200 bg-white"
                    )}
                    onClick={() => setLoadoutSkin(skin.id)}
                  >
                    <img className="h-10 w-10 rounded-md object-cover" src={skin.imageSrc} alt={skin.label} />
                    <span className="text-sm font-semibold text-slate-700">{skin.label}</span>
                  </button>
                ))}
              </div>
            </section>
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

function SectionHeader({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <header>
      <small className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">{eyebrow}</small>
      <strong className="mt-1 block text-lg text-slate-950">{title}</strong>
    </header>
  );
}

function toneSurfaceClass(tone: LoadoutTone): string {
  switch (tone) {
    case "cyan":
      return "border-cyan-200 bg-cyan-50 text-cyan-950";
    case "gold":
      return "border-amber-200 bg-amber-50 text-amber-950";
    case "ice":
      return "border-sky-200 bg-sky-50 text-sky-950";
  }
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
      eyebrow: "Forum",
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
      eyebrow: "Mails",
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
