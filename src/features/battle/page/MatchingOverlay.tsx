import { Link } from "react-router-dom";
import type { getLoadoutPresets, getLoadoutSummary } from "../../loadout/loadoutGateway";
import { formatMatchmakingTime } from "./battlePageTypes";
import type { MatchmakingQueueState } from "./matchmakingQueueTypes";

interface MatchingOverlayProps {
  countdownMs: number;
  loadout: ReturnType<typeof getLoadoutSummary>;
  presets: ReturnType<typeof getLoadoutPresets>;
  queueState: MatchmakingQueueState | null;
  onPresetChange: (presetId: string) => void;
}

export function MatchingOverlay({ countdownMs, loadout, presets, queueState, onPresetChange }: MatchingOverlayProps) {
  const slots = buildSlots(loadout.handle, queueState);

  return (
    <div className="arena-shell__overlay arena-shell__overlay--matching">
      <div className="match-board">
        <div className="match-board__topline">
          <small>{queueState ? `ROOM ${queueState.matchId.slice(0, 12)}` : "LOCAL PRE-MATCH BOARD"}</small>
          <strong>{formatMatchmakingTime(countdownMs)}</strong>
        </div>

        <aside className="match-board__profile">
          <div className="match-board__badge">
            <img src={loadout.skinImageSrc} alt={loadout.skinLabel} />
          </div>
          <span className="match-board__badge-label">{loadout.presetLabel}</span>
          <small>{loadout.modeLabel}</small>
          <strong>{loadout.handle}</strong>
          <span>{loadout.primary}</span>
          <span className="match-board__profile-note">{loadout.presetDescription}</span>
          <div className="match-board__skill-tags">
            {loadout.skills.map((skill) => (
              <span key={skill}>{skill}</span>
            ))}
          </div>
        </aside>

        <section className="match-board__main">
          <header className="match-board__header">
            <h2>准备空降</h2>
            <p>倒计时结束后自动进入 6 人竞技场，主按钮仅保留准备态，不会跳过等待。</p>
          </header>

          <div className="match-board__preset-strip" role="tablist" aria-label="loadout presets">
            {presets.map((preset) => (
              <button
                key={preset.id}
                type="button"
                role="tab"
                aria-selected={preset.id === loadout.presetId}
                className={`match-board__preset${preset.id === loadout.presetId ? " match-board__preset--active" : ""}`}
                onClick={() => onPresetChange(preset.id)}
              >
                <small>{preset.label}</small>
                <strong>{preset.primary}</strong>
                <span>{preset.description}</span>
              </button>
            ))}
          </div>

          <div className="match-board__kit">
            <article>
              <small>当前预设</small>
              <strong>{loadout.presetLabel}</strong>
            </article>
            <article>
              <small>技能</small>
              <strong>{loadout.skills.join(" / ")}</strong>
            </article>
            <article>
              <small>模式</small>
              <strong>{loadout.modeLabel}</strong>
            </article>
          </div>

          <div className="match-board__slots">
            {slots.map((slot) => (
              <article
                key={slot.slot}
                className={`match-board__slot match-board__slot--${slot.tone}`}
                aria-current={slot.tone === "player" ? "true" : undefined}
                aria-disabled={slot.tone === "bot" ? "true" : undefined}
                aria-label={`${slot.slot} ${slot.name} ${slot.tone === "bot" ? "CPU LOCKED" : slot.note}`}
              >
                <strong>{slot.slot}</strong>
                <span>{slot.name}</span>
                <small>{slot.tone === "bot" ? "CPU LOCKED" : slot.note}</small>
              </article>
            ))}
          </div>

          <div className="match-board__actions">
            <Link className="match-board__action match-board__action--ghost" to="/loadout">
              配装
            </Link>
            <button type="button" className="match-board__action match-board__action--primary" aria-pressed="false">
              准备
            </button>
            <Link className="match-board__action" to="/">
              取消
            </Link>
          </div>
        </section>

        <aside className="match-board__rail">
          <article>
            <small>ARENA</small>
            <strong>6 人</strong>
          </article>
          <article>
            <small>ROUND</small>
            <strong>5 分钟</strong>
          </article>
          <article>
            <small>RATING</small>
            <strong>{loadout.rating}</strong>
          </article>
          <article className="match-board__clock">
            <small>ENTER BATTLE</small>
            <strong>{formatMatchmakingTime(countdownMs)}</strong>
          </article>
        </aside>
      </div>
    </div>
  );
}

type MatchSlot = {
  slot: string;
  name: string;
  note: string;
  tone: "player" | "bot";
};

function buildSlots(handle: string, queueState: MatchmakingQueueState | null): MatchSlot[] {
  const capacity = queueState?.capacity ?? 6;
  const humanPlayers = queueState ? queueState.players.slice(0, capacity) : [{ handle, joinedAt: Date.now() }];
  const humanSlots = humanPlayers.map((player, index) => ({
    slot: `P${index + 1}`,
    name: player.handle,
    note: sameHandle(player.handle, handle) ? "YOU" : "ONLINE",
    tone: "player" as const
  }));
  const botCount = Math.max(0, capacity - humanSlots.length);
  const botSlots = Array.from({ length: botCount }, (_, index) => ({
    slot: `B${index + 1}`,
    name: `机器人${index + 1}`,
    note: "CPU",
    tone: "bot" as const
  }));

  return [...humanSlots, ...botSlots];
}

function sameHandle(left: string, right: string): boolean {
  return left.trim().toLowerCase() === right.trim().toLowerCase();
}
