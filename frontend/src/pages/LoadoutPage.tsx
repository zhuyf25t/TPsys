import { useState, useSyncExternalStore } from "react";
import {
  getAuthSkinOptions,
  getCurrentAuthUser,
  logoutLocalUser,
  subscribeAuthState
} from "../features/auth/authGateway";
import { fetchDiscussionSummaries, getDiscussionSummaries } from "../features/forum/forumGateway";
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
} from "../features/loadout/loadoutGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  REMOTE_MAIL_REFRESH_INTERVAL_MS,
  markMailAsReadRemote
} from "../features/mails/mailsGateway";
import { getRatingEntries, loadRatingEntries } from "../features/rating/ratingGateway";
import { getReplaySummaries, loadReplaySummaries } from "../features/replay/replayGateway";
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
  const selectedSlotBySkillId = new Map<LoadoutSkillId, SkillSlotKey>(
    skillSlots.map((slot) => [slot.skillId, slot.key])
  );
  const unassignedSkillOptions = skillOptions.filter((skill) => !selectedSkillIds.has(skill.id));
  const replaySummaries = useLobbyData(() => getReplaySummaries(), loadReplaySummaries, []);
  const discussionSummaries = useLobbyData(() => getDiscussionSummaries(), fetchDiscussionSummaries, []);
  const shouldRefreshRemoteMail = isRemoteMailSourceConfigured() && Boolean(authUser?.handle?.trim());
  const mailSummaries = useLobbyData(
    () => getMailSummaries(),
    () => loadMergedMailSummaries(authUser?.handle),
    [authUser?.handle],
    {
      refreshIntervalMs: shouldRefreshRemoteMail ? REMOTE_MAIL_REFRESH_INTERVAL_MS : 0,
      refreshOnFocus: shouldRefreshRemoteMail,
      refreshEvents: [MAIL_SUMMARIES_CHANGED_EVENT]
    }
  );
  const unreadMailCount = mailSummaries.filter((mail) => mail.unread).length;
  const quickActionsWithBadges = quickActions.map((action) =>
    action.key === "mails" ? { ...action, badgeCount: unreadMailCount } : action
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

  const previewSets = buildPreviewSets(
    replaySummaries,
    discussionSummaries,
    mailSummaries,
    ratingEntries,
    authUser?.handle
  );

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
        playerMeta={authUser ? "已登录 · 本地配装保存" : "访客模式 · 登录后保存"}
        playerRating={String(loadout.rating)}
        currentLoadoutLabel={`${loadout.presetLabel} · ${loadout.skinLabel}`}
        skillTags={loadout.skills}
        quickActions={quickActionsWithBadges}
        previewSets={previewSets}
        primaryAction={
          authUser
            ? { label: "开始", to: "/battle?new=1", variant: "primary" }
            : { label: "登录", onClick: () => setAuthMode("login"), variant: "primary" }
        }
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
          { label: "ARENA", value: "6 人" },
          { label: "ROUND", value: "5 分钟" },
          { label: "RULE", value: "单命" }
        ]}
        menuBody={
          <div className="loadout-console loadout-console--skill-pass">
            <div className="loadout-console__status">
              <div className="loadout-console__status-line">
                <span>Q / E / R 技能槽</span>
                <strong>{armedSlot ? `正在编辑 ${armedSlot}` : "点击槽位，再点击技能替换；点击已装备技能可定位槽位。"}</strong>
              </div>
              <div className="loadout-console__status-tags" aria-label="配装状态">
                <span className="loadout-console__tag">已装备 {skillSlots.length}</span>
                <span className="loadout-console__tag">未装备 {unassignedSkillOptions.length}</span>
                <span className="loadout-console__tag loadout-console__tag--dim">
                  {armedSlot ? "选择技能或点另一槽交换" : "待机"}
                </span>
              </div>
            </div>

            <section className="loadout-console__section loadout-console__section--skills">
              <header>
                <small>Skill Slots</small>
                <strong>技能键位</strong>
              </header>
              <p className="loadout-console__hint">
                {armedSlot
                  ? `当前选中 ${armedSlot} 槽：点技能会替换；点另一个槽位会交换。`
                  : "先点 Q / E / R 槽位，再从技能池选择 Blink / Dash / Freeze。"}
              </p>

              <div className="loadout-slot-row" aria-label="已选技能槽">
                {skillSlots.map((slot) => (
                  <button
                    key={slot.key}
                    type="button"
                    className={`loadout-slot loadout-slot--${slot.tone}${armedSlot === slot.key ? " loadout-slot--armed" : ""}`}
                    aria-pressed={armedSlot === slot.key}
                    onClick={() => handleSlotClick(slot.key)}
                  >
                    <span className="loadout-slot__key">{slot.key}</span>
                    <strong>{slot.label}</strong>
                    <small>{slot.description}</small>
                  </button>
                ))}
              </div>

              <div className="loadout-skill-bank" aria-label="技能池">
                <div className="loadout-skill-bank__group">
                  <small>Skill Bank</small>
                  <div className="loadout-skill-bank__tokens">
                    {skillOptions.map((skill) => {
                      const selectedSlot = selectedSlotBySkillId.get(skill.id);
                      const isSelected = Boolean(selectedSlot);

                      return (
                        <button
                          key={skill.id}
                          type="button"
                          className={`loadout-skill-token loadout-skill-token--${skill.tone}${
                            isSelected ? " loadout-skill-token--selected" : " loadout-skill-token--unselected"
                          }${selectedSlot === armedSlot ? " loadout-skill-token--armed-source" : ""}${
                            armedSlot ? " loadout-skill-token--ready" : ""
                          }`}
                          aria-pressed={isSelected}
                          onClick={() => handleSkillClick(skill.id)}
                        >
                          <span>{skill.shortLabel}</span>
                          <strong>{skill.label}</strong>
                          <small>{selectedSlot ? `${selectedSlot} 已选中` : "未选中"}</small>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="loadout-skill-bank__group loadout-skill-bank__group--dim">
                  <small>Unselected</small>
                  <div className="loadout-skill-bank__tokens">
                    {unassignedSkillOptions.length ? (
                      unassignedSkillOptions.map((skill) => (
                        <button
                          key={skill.id}
                          type="button"
                          className={`loadout-skill-token loadout-skill-token--${skill.tone} loadout-skill-token--unselected`}
                          onClick={() => handleSkillClick(skill.id)}
                        >
                          <span>{skill.shortLabel}</span>
                          <strong>{skill.label}</strong>
                        </button>
                      ))
                    ) : (
                      <span className="loadout-skill-bank__empty">当前仅开放 Blink / Dash / Freeze，三项均已入槽。</span>
                    )}
                  </div>
                </div>
              </div>
            </section>

            <section className="loadout-console__section loadout-console__section--weapons">
              <header>
                <small>Weapon Preset</small>
                <strong>武器打法</strong>
              </header>
              <div className="loadout-console__cards">
                {presets.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    className={`loadout-card${preset.id === loadout.presetId ? " loadout-card--active" : ""}`}
                    onClick={() => setLoadoutPreset(preset.id)}
                  >
                    <small>{preset.label}</small>
                    <strong>{preset.primary}</strong>
                    <span>{preset.description}</span>
                  </button>
                ))}
              </div>
            </section>

            <section className="loadout-console__section loadout-console__section--skins">
              <header>
                <small>Skin</small>
                <strong>皮肤</strong>
              </header>
              <div className="loadout-skin-strip" aria-label="皮肤选择">
                {skinOptions.map((skin) => (
                  <button
                    key={skin.id}
                    type="button"
                    className={`loadout-skin${skin.id === loadout.skinId ? " loadout-skin--active" : ""}`}
                    onClick={() => setLoadoutSkin(skin.id)}
                  >
                    <img src={skin.imageSrc} alt={skin.label} />
                    <span>{skin.label}</span>
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

function buildPreviewSets(
  replaySummaries: ReturnType<typeof getReplaySummaries>,
  discussionSummaries: ReturnType<typeof getDiscussionSummaries>,
  mailSummaries: ReturnType<typeof getMailSummaries>,
  ratingEntries: ReturnType<typeof getRatingEntries>,
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
        meta: `${replay.resultLabel} · ${replay.finishedAtLabel}`,
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
        meta: `@${topic.author} · ${topic.updatedAt}`,
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
        meta: `${entry.score} · ${entry.title}`,
        detail: `${entry.matchCount} 场 · 胜率 ${entry.winRate}`
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
      eyebrow: "Social",
      detail: "好友申请和社交通知。",
      emptyTitle: "暂无好友申请",
      emptyDetail: "没有真实社交通知时保持空状态。",
      viewAllPath: "/mails",
      anchor: "right",
      items: []
    }
  };
}
