import { Link } from "react-router-dom";
import type { BattlePlayModeId, BattlePlayModeOption } from "../../../runtime/battle/game/maps/battleMapCatalog";
import {
  buildMatchmakingSlots,
  MATCHMAKING_SLOT_COUNT,
  type MatchmakingQueueState
} from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { cn } from "../../../components/ui/classNames";
import { formatMatchmakingTime } from "../hooks/battlePageTypes";

interface MatchingLoadoutSummary {
  handle: string;
  modeLabel: string;
  presetLabel: string;
  primary: string;
  skinImageSrc: string;
  skinLabel: string;
  skills: string[];
}

interface MatchingOverlayProps {
  countdownMs: number;
  loadout: MatchingLoadoutSummary;
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
    <div className="absolute inset-0 z-20 grid place-items-center bg-slate-950/75 p-4 backdrop-blur-sm">
      <div className="grid w-full max-w-6xl gap-5 rounded border border-white/10 bg-slate-950/90 p-5 text-slate-100 shadow-2xl shadow-black/60">
        <header className="flex flex-col gap-4 border-b border-white/10 pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <small className="text-xs font-black uppercase tracking-[0.22em] text-cyan-200">{queueLabel}</small>
            <strong className="mt-2 block text-2xl font-black text-white">{roomIdLabel}</strong>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <Metric label="当前人数" value={`${roomParticipants.length} / ${MATCHMAKING_SLOT_COUNT}`} />
            <Metric label="倒计时" value={countdownLabel} />
            <Metric label="状态" value={phaseLabel} />
          </div>
        </header>

        <section className="grid gap-5 lg:grid-cols-[1fr_360px]">
          <div className="space-y-4">
            <div>
              <h2 className="text-3xl font-black text-white">{queueState ? "准备空降" : "连接房间中"}</h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
                {queueState ? "10 秒后开战，房间会用电脑玩家自动补齐至 6 人。" : "正在向服务器申请房间并同步 6 人对局状态，请稍候。"}
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2" aria-label="玩法选择">
              {battleModeOptions.map((option) => {
                const active = option.modeId === selectedBattleModeId;
                return (
                  <button
                    key={option.modeId}
                    type="button"
                    className={cn(
                      "rounded border p-4 text-left transition disabled:cursor-not-allowed disabled:opacity-50",
                      active ? "border-amber-200/70 bg-amber-300/15 text-amber-50" : "border-white/10 bg-white/[0.04] text-slate-200 hover:border-cyan-300/40 hover:bg-cyan-300/10"
                    )}
                    aria-pressed={active}
                    disabled={modeSelectionDisabled}
                    onClick={() => onBattleModeChange(option.modeId)}
                  >
                    <strong className="block text-lg">{option.label}</strong>
                    <span className="mt-1 block text-sm text-slate-400">{option.mapLabel}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="rounded border border-white/10 bg-white/[0.04] p-4">
            <div className="flex items-center gap-4">
              <div className="grid h-20 w-20 flex-none place-items-center overflow-hidden rounded-full border border-cyan-200/40 bg-cyan-300/10">
                <img className="h-full w-full object-cover" src={loadout.skinImageSrc} alt={loadout.skinLabel} />
              </div>
              <div className="min-w-0">
                <strong className="block truncate text-xl text-white">{loadout.handle}</strong>
                <span className="mt-1 block text-sm text-slate-300">{loadout.presetLabel}</span>
                <small className="mt-1 block text-xs text-slate-400">{loadout.modeLabel}</small>
                <small className="block text-xs text-slate-400">{loadout.primary}</small>
              </div>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {loadout.skills.map((skill) => (
                <span key={skill} className="rounded border border-cyan-200/20 bg-cyan-300/10 px-2 py-1 text-xs font-bold text-cyan-100">
                  {skill}
                </span>
              ))}
            </div>
          </div>
        </section>

        <section className="grid gap-4 lg:grid-cols-[360px_1fr]">
          <div className="rounded border border-white/10 bg-black/20 p-4" aria-label="房间成员">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <small className="text-xs font-bold text-slate-400">房间成员</small>
                <strong className="block text-white">
                  {roomParticipants.length} / {MATCHMAKING_SLOT_COUNT}
                </strong>
              </div>
              <span className="text-xs text-slate-400">{queueState ? "等待成员进入" : "连接匹配服务中"}</span>
            </div>
            <div className="grid gap-2">
              {roomParticipants.length > 0 ? (
                roomParticipants.map((participant) => (
                  <ParticipantCard key={participant.playerId} participant={participant} isLocalPlayer={participant.playerId === queueState?.playerId} />
                ))
              ) : (
                <p className="rounded border border-dashed border-white/10 bg-white/[0.03] p-3 text-sm text-slate-400">
                  {queueState ? "等待队员进入。" : "房间成员将在同步完成后显示。"}
                </p>
              )}
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" aria-label="6 个槽位">
            {slots.map((slot) => (
              <article
                key={slot.slotLabel}
                className={cn(
                  "min-h-24 rounded border p-3",
                  slot.isLocalPlayer ? "border-amber-200/60 bg-amber-300/15" : slot.isInteractive ? "border-cyan-200/30 bg-cyan-300/10" : "border-white/10 bg-white/[0.03]"
                )}
                aria-current={slot.isLocalPlayer ? "true" : undefined}
                aria-disabled={slot.isInteractive ? undefined : "true"}
              >
                <span className="text-xs font-black uppercase tracking-[0.16em] text-slate-400">{slot.slotLabel}</span>
                <strong className="mt-2 block text-white">{slot.title}</strong>
                <small className="mt-1 block text-xs text-slate-400">{slot.detail}</small>
              </article>
            ))}
          </div>
        </section>

        <footer className="flex flex-wrap justify-end gap-3 border-t border-white/10 pt-4">
          <Link className="rounded border border-white/10 bg-white/5 px-4 py-2 text-sm font-bold text-slate-200 transition hover:bg-white/10" to="/loadout">
            返回配置
          </Link>
          <Link className="rounded border border-white/10 bg-white/5 px-4 py-2 text-sm font-bold text-slate-200 transition hover:bg-white/10" to="/">
            取消
          </Link>
          <button type="button" className="rounded border border-amber-200/40 bg-amber-300/15 px-4 py-2 text-sm font-black text-amber-50" disabled>
            {queueState ? "准备中" : "等待同步"}
          </button>
        </footer>
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded border border-white/10 bg-white/[0.04] px-4 py-3">
      <small className="text-xs font-bold text-slate-400">{label}</small>
      <strong className="mt-1 block text-lg text-white">{value}</strong>
    </article>
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
    <article className={cn("rounded border px-3 py-2", isLocalPlayer ? "border-amber-200/50 bg-amber-300/10" : "border-white/10 bg-white/[0.04]")}>
      <strong className="block text-sm text-white">{participant.handle}</strong>
      <span className="text-xs text-slate-400">{isLocalPlayer ? "自己" : "真实玩家"}</span>
      <small className="ml-2 text-xs text-slate-500">{participant.rating === undefined ? "评分待定" : `评分 ${participant.rating}`}</small>
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
