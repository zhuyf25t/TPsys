import { Link } from "react-router-dom";
import type { LocalBattleReturnSummary } from "../../../../../runtime/battle/local/state/battleLocalGateway";

interface BattleSettlementOverlayProps {
  summary: LocalBattleReturnSummary;
  replayId: string | null;
  onNewMatch: () => void;
}

/** 中文名：结算层（BattleSettlementOverlay）。游戏职责：展示本局结果、回放入口和新一局入口。 */
export function BattleSettlementOverlay({ summary, replayId, onNewMatch }: BattleSettlementOverlayProps) {
  return (
    <div className="absolute inset-0 z-40 grid place-items-center bg-slate-950/75 p-4 backdrop-blur-sm">
      <div className="w-full max-w-3xl rounded border border-white/10 bg-slate-950/95 p-5 text-slate-100 shadow-2xl shadow-black/60">
        <div className="border-b border-white/10 pb-4">
          <small className="text-xs font-black uppercase tracking-[0.24em] text-amber-200">SETTLEMENT</small>
          <h2 className="mt-2 text-3xl font-black text-white">本局结束</h2>
          <p className="mt-2 text-sm leading-6 text-slate-300">
            排名 {summary.placement ? `#${summary.placement}` : "--"} / 得分 {summary.score} / 用时 {summary.durationLabel}
          </p>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-3">
          {summary.settlementCards.slice(0, 3).map((card) => (
            <article key={card.label} className="rounded border border-white/10 bg-white/[0.04] p-4">
              <small className="text-xs font-bold text-slate-400">{card.label}</small>
              <strong className="mt-2 block text-2xl font-black text-white">{card.value}</strong>
            </article>
          ))}
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-4">
          <button
            type="button"
            className="rounded border border-amber-200/50 bg-amber-300/20 px-4 py-3 text-sm font-black text-amber-50 transition hover:bg-amber-300/30"
            onClick={onNewMatch}
          >
            再来一局
          </button>
          <Link className="rounded border border-cyan-200/40 bg-cyan-300/10 px-4 py-3 text-center text-sm font-black text-cyan-50 transition hover:bg-cyan-300/20" to={replayId ? `/replay/${replayId}` : "/replay"}>
            查看回放
          </Link>
          <Link className="rounded border border-cyan-200/40 bg-cyan-300/10 px-4 py-3 text-center text-sm font-black text-cyan-50 transition hover:bg-cyan-300/20" to="/rating">
            查看变化
          </Link>
          <Link className="rounded border border-white/10 bg-white/5 px-4 py-3 text-center text-sm font-black text-slate-200 transition hover:bg-white/10" to="/">
            返回大厅
          </Link>
        </div>
      </div>
    </div>
  );
}
