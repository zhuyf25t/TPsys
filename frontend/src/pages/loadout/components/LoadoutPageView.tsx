import { AuthOverlay } from "../../shared/components/auth/AuthOverlay";
import type { LoadoutPageState } from "../hooks/useLoadoutPage";
import { LobbyShell } from "../../../components/ui/LobbyShell";

/** 中文名称：配装页视图。游戏职责：渲染技能槽、武器预设和皮肤选择。 */
export function LoadoutPageView({
  armedSlot,
  authMode,
  battleCapacityLabel,
  closeAuthOverlay,
  completeAuth,
  currentLoadoutLabel,
  handleSkillClick,
  handleSlotClick,
  isAuthenticated,
  loadout,
  presets,
  previewSets,
  primaryAction,
  quickActions,
  railItems,
  roundDurationLabel,
  secondaryAction,
  selectPreset,
  selectSkin,
  selectedSlotBySkillId,
  skillOptions,
  skillSlots,
  skinOptions,
  tertiaryAction,
  unassignedSkillOptions
}: LoadoutPageState) {
  return (
    <>
      <LobbyShell
        layoutMode="solo"
        brand="LOADOUT"
        title="战前配装"
        subtitle="选择 Q / E / R 技能槽、武器打法和皮肤，保持大厅游戏菜单体验。"
        playerName={isAuthenticated ? loadout.handle : "访客"}
        playerBadge={isAuthenticated ? "P1" : "GUEST"}
        playerAvatarSrc={loadout.skinImageSrc}
        playerMeta={isAuthenticated ? "已登录 · 本地配装保存" : "访客模式 · 登录后保存"}
        playerRating={String(loadout.rating)}
        currentLoadoutLabel={currentLoadoutLabel}
        skillTags={loadout.skills}
        quickActions={quickActions}
        previewSets={previewSets}
        primaryAction={primaryAction}
        secondaryAction={secondaryAction}
        tertiaryAction={tertiaryAction}
        railItems={railItems}
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
                <span className="loadout-console__tag loadout-console__tag--dim">{armedSlot ? "选择技能或点另一槽交换" : "待机"}</span>
              </div>
            </div>

            <section className="loadout-console__section loadout-console__section--skills">
              <header>
                <small>Skill Slots</small>
                <strong>技能键位</strong>
              </header>
              <p className="loadout-console__hint">
                {armedSlot ? `当前选中 ${armedSlot} 槽：点技能会替换；点另一个槽位会交换。` : "先点 Q / E / R 槽位，再从技能池选择 Blink / Dash / Freeze。"}
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
                          }${selectedSlot === armedSlot ? " loadout-skill-token--armed-source" : ""}${armedSlot ? " loadout-skill-token--ready" : ""}`}
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
                    onClick={() => selectPreset(preset.id)}
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
                  <button key={skin.id} type="button" className={`loadout-skin${skin.id === loadout.skinId ? " loadout-skin--active" : ""}`} onClick={() => selectSkin(skin.id)}>
                    <img src={skin.imageSrc} alt={skin.label} />
                    <span>{skin.label}</span>
                  </button>
                ))}
              </div>
            </section>

            <div className="loadout-console__status-tags" aria-label="规则摘要">
              <span className="loadout-console__tag">竞技场容量：{battleCapacityLabel}</span>
              <span className="loadout-console__tag">回合时长：{roundDurationLabel}</span>
            </div>
          </div>
        }
      />

      {authMode ? <AuthOverlay initialMode={authMode} onClose={closeAuthOverlay} onSuccess={completeAuth} /> : null}
    </>
  );
}
