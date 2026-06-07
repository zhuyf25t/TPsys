import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "../../../../../../components/ui/classNames";
import type { BattlePlayModeId, BattlePlayModeOption } from "../../../../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import {
  buildMatchmakingSlots,
  resolveMatchmakingSlotCount,
  type MatchmakingQueueState,
  type MatchmakingSeatKind,
  type MatchmakingSlotState
} from "../../../../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { formatMatchmakingTime } from "../../../../functions/formatMatchmakingTime";
import { formatPhaseLabel, formatQueueLabel, shortenRoomId } from "./functions/matchingOverlayLabels";
import type { MatchingLoadoutSummary } from "./objects/MatchingLoadoutSummary";
import { MATCHMAKING_DURATION_MS } from "../../../../objects/BattlePageTiming";

interface MatchingOverlayProps {
  countdownMs: number;
  loadout: MatchingLoadoutSummary;
  queueState: MatchmakingQueueState | null;
  selectedBattleModeId: BattlePlayModeId;
  battleModeOptions: readonly BattlePlayModeOption[];
  onBattleModeChange: (modeId: BattlePlayModeId) => void;
}

const ACTIVITY_TIME_LABELS = ["20:17", "20:16", "20:15", "20:14"];

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
  const slotCount = resolveMatchmakingSlotCount(queueState, selectedBattleModeId);
  const slots = buildMatchmakingSlots(loadout.handle, queueState, selectedBattleModeId);
  const occupiedSlotCount = countOccupiedSlots(slots);
  const roomIdLabel = queueState ? shortenRoomId(queueState.roomId).toUpperCase() : "分配房间中";
  const queueLabel = formatQueueLabel(queueState);
  const phaseLabel = formatPhaseLabel(queueState);
  const countdownLabel = formatMatchmakingTime(countdownMs);
  const selectedMode = battleModeOptions.find((option) => option.modeId === selectedBattleModeId) ?? battleModeOptions[0];
  const mapLabel = translateMapLabel(queueState?.mapLabel ?? selectedMode?.mapLabel ?? "Battle Rift");
  const modeLabel = translateModeLabel(queueState?.modeLabel ?? selectedMode?.label ?? "Battle Rift");
  const progress = resolveCountdownProgress(countdownMs, queueState);
  const progressStyle = { "--matchmaking-progress": `${Math.round(progress * 360)}deg` } as CSSProperties;

  return (
    <div className="matching-room" role="dialog" aria-modal="true" aria-label="Battle Rift 等待房间">
      <div className="matching-room__grid" aria-hidden="true" />
      <div className="matching-room__scanline" aria-hidden="true" />

      <header className="matching-room__topbar">
        <div className="matching-room__room-status" aria-label="房间状态">
          <span>WAITING ROOM</span>
          <strong>{modeLabel}</strong>
          <small>{mapLabel}</small>
        </div>
        <div className="matching-room__profile-strip">
          <img src={loadout.skinImageSrc} alt="" />
          <div>
            <strong>{loadout.handle}</strong>
            <span>评分 {resolveLocalRating(roomParticipants, loadout.handle)}</span>
          </div>
        </div>
      </header>

      <main className="matching-room__layout">
        <aside className="matching-room__side matching-room__side--left">
          <InfoPanel title="房间信息">
            <InfoLine label="房主" value={loadout.handle} />
            <InfoLine label="房间类型" value={queueState?.source === "local" ? "本地演练" : "排位队列"} />
            <InfoLine label="队列状态" value={queueLabel} />
            <InfoLine label="服务器" value="自动选择" />
            <InfoLine label="当前状态" value={phaseLabel} />
            <button type="button" className="matching-room__thin-button">邀请好友</button>
          </InfoPanel>

          <InfoPanel title="队伍排行">
            <div className="matching-room__leaderboard">
              {buildLeaderboardRows(slots).map((row, index) => (
                <div key={`${row.title}-${index}`} className="matching-room__leaderboard-row">
                  <span>{index + 1}</span>
                  <strong>{row.title}</strong>
                  <em>{row.score}</em>
                </div>
              ))}
            </div>
          </InfoPanel>
        </aside>

        <section className="matching-room__center">
          <div className="matching-room__hero-panel">
            <div className="matching-room__title-frame">
              <span>等待房间</span>
              <h1>BATTLE RIFT SQUAD</h1>
              <p>
                房间编号：<strong>{roomIdLabel}</strong>
              </p>
            </div>

            <div className="matching-room__team-header">
              <span>队伍 {occupiedSlotCount} / {slotCount}</span>
              <em>{queueState && occupiedSlotCount >= slotCount ? "所有席位已准备，等待开战同步..." : queueState ? "等待所有队员准备..." : "正在连接匹配服务..."}</em>
            </div>

            <div className="matching-room__slot-row" aria-label="Matchmaking slots">
              {slots.map((slot, index) => (
                <PlayerSeatCard
                  key={`${slot.slotLabel}-${index}`}
                  slot={slot}
                  index={index}
                  localSkinImageSrc={loadout.skinImageSrc}
                />
              ))}
            </div>

            <div className="matching-room__console-grid">
              <section className="matching-room__console-panel matching-room__mode-panel">
                <div className="matching-room__panel-title">游戏模式</div>
                <div className="matching-room__mode-content">
                  <div className="matching-room__skull-badge" aria-hidden="true" />
                  <div>
                    <h2>{modeLabel}</h2>
                    <p>压制敌方玩家，占住裂隙核心，队伍保持火力优势即可取得胜利。</p>
                  </div>
                </div>
                <div
                  className="matching-room__mode-options"
                  aria-label="战斗模式选项"
                >
                  {battleModeOptions.map((option) => {
                    const active = option.modeId === selectedBattleModeId;
                    return (
                      <button
                        key={option.modeId}
                        type="button"
                        className={cn("matching-room__mode-option", active && "matching-room__mode-option--active")}
                        aria-pressed={active}
                        onClick={() => onBattleModeChange(option.modeId)}
                      >
                        <span>{translateModeLabel(option.label)}</span>
                        <small>{translateMapLabel(option.mapLabel)}</small>
                      </button>
                    );
                  })}
                </div>
              </section>

              <section className="matching-room__console-panel matching-room__map-panel">
                <div className="matching-room__panel-title">作战地图</div>
                <div className="matching-room__map-preview">
                  <div className="matching-room__map-glow" aria-hidden="true" />
                  <strong>{mapLabel}</strong>
                  <span>{modeLabel}</span>
                </div>
                <div className="matching-room__map-lock">
                  <span>当前地图已锁定</span>
                  <strong>{mapLabel}</strong>
                </div>
              </section>

              <section className="matching-room__console-panel matching-room__timer-panel">
                <div className="matching-room__panel-title">开战倒计时</div>
                <div className="matching-room__timer-ring" style={progressStyle}>
                  <strong>{countdownLabel}</strong>
                </div>
                <span className="matching-room__timer-caption">预计 {occupiedSlotCount} / {slotCount} 名席位</span>
                <div className="matching-room__timer-bars" aria-hidden="true">
                  {Array.from({ length: slotCount }).map((_, index) => (
                    <i key={index} className={index < occupiedSlotCount ? "is-filled" : ""} />
                  ))}
                </div>
              </section>
            </div>
          </div>
        </section>

        <aside className="matching-room__side matching-room__side--right">
          <InfoPanel title="房间聊天">
            <div className="matching-room__chat">
              <ChatLine name={loadout.handle} time="20:14" text="配装已经同步完毕。" />
              <ChatLine name="系统" time="20:15" text={`已锁定${modeLabel}。`} />
              <ChatLine name="小队" time="20:16" text="保持阵型，等待投放。" />
              <ChatLine name="系统" time="20:17" text={`${Math.max(roomParticipants.length, 1)} 名玩家在线。`} />
            </div>
            <div className="matching-room__chat-input">
              <span>输入消息...</span>
              <button type="button" aria-label="发送消息">&gt;</button>
            </div>
          </InfoPanel>

          <InfoPanel title="房间动态">
            <div className="matching-room__activity">
              {buildActivityRows(slots).map((row, index) => (
                <div key={`${row}-${index}`}>
                  <span>{ACTIVITY_TIME_LABELS[index % ACTIVITY_TIME_LABELS.length]}</span>
                  <strong>{row}</strong>
                </div>
              ))}
            </div>
          </InfoPanel>
        </aside>
      </main>

      <footer className="matching-room__dock">
        <Link className="matching-room__dock-button matching-room__dock-button--loadout" to="/loadout">
          <span className="matching-room__dock-icon" aria-hidden="true" />
          配装
        </Link>
        <button type="button" className="matching-room__ready-button" disabled>
          <strong>{queueState ? "准备" : "同步中"}</strong>
          <span>{queueState ? "开始匹配" : "等待房间"}</span>
        </button>
        <Link className="matching-room__dock-button matching-room__dock-button--exit" to="/">
          退出房间
        </Link>
      </footer>
    </div>
  );
}

interface InfoPanelProps {
  title: string;
  children: ReactNode;
}

function InfoPanel({ title, children }: InfoPanelProps) {
  return (
    <section className="matching-room__info-panel">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

interface InfoLineProps {
  label: string;
  value: string;
}

function InfoLine({ label, value }: InfoLineProps) {
  return (
    <div className="matching-room__info-line">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

interface PlayerSeatCardProps {
  slot: MatchmakingSlotState;
  index: number;
  localSkinImageSrc: string;
}

function PlayerSeatCard({ slot, index, localSkinImageSrc }: PlayerSeatCardProps) {
  const occupied = slot.kind !== "empty";

  return (
    <article
      className={cn(
        "matching-room__seat",
        `matching-room__seat--${slot.kind}`,
        slot.isLocalPlayer && "matching-room__seat--host"
      )}
      aria-current={slot.isLocalPlayer ? "true" : undefined}
      aria-disabled={occupied ? undefined : "true"}
    >
      <div className="matching-room__seat-tag">{slot.isLocalPlayer ? "房主" : String(index + 1)}</div>
      <div className="matching-room__avatar-frame">
        {slot.isLocalPlayer ? (
          <img src={localSkinImageSrc} alt="" />
        ) : occupied ? (
          <div className="matching-room__avatar-silhouette" aria-hidden="true" />
        ) : (
          <div className="matching-room__invite-plus" aria-hidden="true">+</div>
        )}
      </div>
      <strong>{slot.title}</strong>
      <span>{seatRatingLabel(slot)}</span>
      <em>{seatStatusLabel(slot.kind)}</em>
    </article>
  );
}

interface ChatLineProps {
  name: string;
  time: string;
  text: string;
}

function ChatLine({ name, time, text }: ChatLineProps) {
  return (
    <div className="matching-room__chat-line">
      <div>
        <strong>{name}</strong>
        <time>{time}</time>
      </div>
      <span>{text}</span>
    </div>
  );
}

function countOccupiedSlots(slots: MatchmakingSlotState[]): number {
  return slots.filter((slot) => slot.kind !== "empty").length;
}

function resolveCountdownProgress(countdownMs: number, queueState: MatchmakingQueueState | null): number {
  const durationMs = Math.max(1, queueState?.durationMs ?? MATCHMAKING_DURATION_MS);
  const remainingMs = Math.max(0, Math.min(durationMs, countdownMs));
  return 1 - remainingMs / durationMs;
}

function resolveLocalRating(participants: MatchmakingQueueState["participants"], handle: string): string {
  const participant = participants.find((entry) => entry.handle.toLowerCase() === handle.toLowerCase());
  return participant?.rating === undefined ? "1200" : String(participant.rating);
}

function seatRatingLabel(slot: MatchmakingSlotState): string {
  if (slot.kind === "empty") {
    return "邀请玩家";
  }

  const ratingMatch = slot.detail.match(/\d{3,5}/);
  return ratingMatch ? `评分 ${ratingMatch[0]}` : slot.kind === "bot" ? "智能队友" : "评分 1200";
}

function seatStatusLabel(kind: MatchmakingSeatKind): string {
  if (kind === "empty") {
    return "空位";
  }

  return kind === "player" ? "未准备" : "已准备";
}

function buildLeaderboardRows(slots: MatchmakingSlotState[]): Array<{ title: string; score: string }> {
  return slots
    .filter((slot) => slot.kind !== "empty")
    .slice(0, 8)
    .map((slot, index) => ({
      title: slot.title,
      score: slot.kind === "bot" ? String(1200 - index * 7) : seatRatingLabel(slot).replace("评分 ", "")
    }));
}

function buildActivityRows(slots: MatchmakingSlotState[]): string[] {
  const joinedRows = slots
    .filter((slot) => slot.kind !== "empty")
    .slice(0, 4)
    .map((slot) => `${slot.title} 已进入房间`);

  return joinedRows.length > 0 ? joinedRows : ["房间已创建", "等待小队集结", "配装已同步"];
}

function translateModeLabel(label: string): string {
  const normalized = label.trim().toLowerCase();
  if (normalized.includes("zombie") || normalized.includes("winter") || normalized.includes("丧尸")) {
    return "丧尸模式";
  }
  if (normalized.includes("autumn") || normalized.includes("fall") || normalized.includes("秋")) {
    return "秋季狩猎";
  }
  if (normalized.includes("normal") || normalized.includes("standard") || normalized.includes("常规")) {
    return "常规对战";
  }
  if (normalized.includes("team deathmatch")) {
    return "团队死斗";
  }
  return label;
}

function translateMapLabel(label: string): string {
  const normalized = label.trim().toLowerCase();
  if (normalized.includes("winter") || normalized.includes("冬")) {
    return "冬季雪原";
  }
  if (normalized.includes("fall") || normalized.includes("autumn") || normalized.includes("秋")) {
    return "秋季海岛";
  }
  if (normalized.includes("normal") || normalized.includes("standard") || normalized.includes("常规")) {
    return "常规战区";
  }
  if (normalized.includes("industrial") || normalized.includes("arena")) {
    return "工业竞技场";
  }
  if (normalized.includes("battle rift")) {
    return "裂隙战区";
  }
  return label;
}
