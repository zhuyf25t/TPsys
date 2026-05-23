import { Link } from "react-router-dom";
import type { getLoadoutSummary } from "../../../api/loadoutGateway";
import { formatMatchmakingTime } from "./battlePageTypes";
import {
  buildMatchmakingSlots,
  MATCHMAKING_SLOT_COUNT,
  type MatchmakingQueueState
} from "../../../runtime/matchmaking/matchmakingQueueTypes";
import type { BattlePlayModeId, BattlePlayModeOption } from "../../../game/maps/battleMapCatalog";

interface MatchingOverlayProps {
  countdownMs: number;
  loadout: ReturnType<typeof getLoadoutSummary>;
  queueState: MatchmakingQueueState | null;
  selectedBattleModeId: BattlePlayModeId;
  battleModeOptions: readonly BattlePlayModeOption[];
  onBattleModeChange: (modeId: BattlePlayModeId) => void;
}

/** 中文名：匹配等待层（MatchingOverlay）。游戏职责：展示等待房间、玩家席位和战斗玩法选择。 */
export function MatchingOverlay({
  countdownMs,
  loadout,
  queueState,
  selectedBattleModeId,
  battleModeOptions,
  onBattleModeChange
}: MatchingOverlayProps) {
  const roomParticipants = queueState?.participants ?? [];
  const slots = buildMatchmakingSlots(loadout.handle, queueState);
  const roomIdLabel = queueState ? shortenRoomId(queueState.roomId) : "等待服务器分配房间";
  const queueLabel = formatQueueLabel(queueState);
  const phaseLabel = formatPhaseLabel(queueState);
  const countdownLabel = queueState ? formatMatchmakingTime(countdownMs) : "-";
  const modeSelectionDisabled = queueState?.phase === "active";

  return (
    <div className="arena-shell__overlay arena-shell__overlay--matching">
      <div className="match-board">
        <header className="match-board__header match-board__header--matching">
          <div className="match-board__headline">
            <small>{queueLabel}</small>
            <strong>{roomIdLabel}</strong>
          </div>
          <div className="match-board__header-metrics">
            <article>
              <small>当前人数</small>
              <strong>
                {roomParticipants.length} / {MATCHMAKING_SLOT_COUNT}
              </strong>
            </article>
            <article>
              <small>倒计时</small>
              <strong>{countdownLabel}</strong>
            </article>
            <article>
              <small>状态</small>
              <strong>{phaseLabel}</strong>
            </article>
          </div>
        </header>

        <section className="match-board__summary">
          <div className="match-board__summary-copy">
            <h2>{queueState ? "准备空降" : "连接房间中"}</h2>
            <p>
              {queueState
                ? "10 秒后开战，房间会用电脑玩家自动补齐至 6 人。"
                : "正在向服务器申请房间并同步 6 人对局状态，请稍候。"}
            </p>
          </div>

          <div className="match-board__mode-picker" aria-label="玩法选择">
            {battleModeOptions.map((option) => {
              const active = option.modeId === selectedBattleModeId;
              return (
                <button
                  key={option.modeId}
                  type="button"
                  className={`match-board__mode-option${active ? " match-board__mode-option--active" : ""}`}
                  aria-pressed={active}
                  disabled={modeSelectionDisabled}
                  onClick={() => onBattleModeChange(option.modeId)}
                >
                  <strong>{option.label}</strong>
                  <span>{option.mapLabel}</span>
                </button>
              );
            })}
          </div>

          <div className="match-board__summary-card">
            <div className="match-board__badge">
              <img src={loadout.skinImageSrc} alt={loadout.skinLabel} />
            </div>
            <div className="match-board__summary-meta">
              <strong>{loadout.handle}</strong>
              <span>{loadout.presetLabel}</span>
              <small>{loadout.modeLabel}</small>
              <small>{loadout.primary}</small>
            </div>
            <div className="match-board__skill-tags">
              {loadout.skills.map((skill) => (
                <span key={skill}>{skill}</span>
              ))}
            </div>
          </div>
        </section>

        <section className="match-board__presence" aria-label="房间成员">
          <div className="match-board__section-head">
            <div>
              <small>房间成员</small>
              <strong>
                {roomParticipants.length} / {MATCHMAKING_SLOT_COUNT}
              </strong>
            </div>
            <span>{queueState ? "等待成员进入" : "连接匹配服务中"}</span>
          </div>

          <div className="match-board__participants">
            {roomParticipants.length > 0 ? (
              roomParticipants.map((participant) => (
                <ParticipantCard
                  key={participant.playerId}
                  participant={participant}
                  isLocalPlayer={participant.playerId === queueState?.playerId}
                />
              ))
            ) : (
              <p className="match-board__presence-empty">
                {queueState ? "等待队员进入。" : "房间成员将在同步完成后显示。"}
              </p>
            )}
          </div>
        </section>

        <section className="match-board__slots" aria-label="6 个槽位">
          {slots.map((slot) => (
            <article
              key={slot.slotLabel}
              className={`match-board__slot match-board__slot--${slot.kind}${slot.isInteractive ? " match-board__slot--interactive" : " match-board__slot--locked"}`}
              aria-current={slot.isLocalPlayer ? "true" : undefined}
              aria-disabled={slot.isInteractive ? undefined : "true"}
            >
              <span className="match-board__slot-tag">{slot.slotLabel}</span>
              <strong>{slot.title}</strong>
              <small>{slot.detail}</small>
            </article>
          ))}
        </section>

        <footer className="match-board__actions">
          <Link className="match-board__action match-board__action--ghost" to="/loadout">
            返回配置
          </Link>
          <Link className="match-board__action" to="/">
            取消
          </Link>
          <button type="button" className="match-board__action match-board__action--primary" disabled>
            {queueState ? "准备中" : "等待同步"}
          </button>
        </footer>
      </div>
    </div>
  );
}

function ParticipantCard({
  participant,
  isLocalPlayer
}: {
  participant: MatchmakingQueueState["participants"][number];
  isLocalPlayer: boolean;
}) {
  return (
    <article className={`match-board__participant${isLocalPlayer ? " match-board__participant--self" : ""}`}>
      <strong>{participant.handle}</strong>
      <span>{isLocalPlayer ? "自己" : "真实玩家"}</span>
      <small>{participant.rating === undefined ? "评分待定" : `评分 ${participant.rating}`}</small>
    </article>
  );
}

function formatQueueLabel(queueState: MatchmakingQueueState | null): string {
  if (!queueState) {
    return "等待匹配";
  }

  if (queueState.phase === "finished") {
    return "战斗已结束";
  }

  return queueState.phase === "active" ? "房间已就绪" : "等待成员";
}

function formatPhaseLabel(queueState: MatchmakingQueueState | null): string {
  if (!queueState) {
    return "等待同步";
  }

  if (queueState.phase === "finished") {
    return "结算中";
  }

  return queueState.phase === "active" ? "即将开战" : "等待成员";
}

function shortenRoomId(roomId: string): string {
  const normalized = roomId.trim();
  if (normalized.length <= 16) {
    return normalized;
  }

  return `${normalized.slice(0, 8)}-${normalized.slice(-6)}`;
}
