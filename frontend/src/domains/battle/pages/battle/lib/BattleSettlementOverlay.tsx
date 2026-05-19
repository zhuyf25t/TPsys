import { Link } from "react-router-dom";
import type { LocalBattleReturnSummary } from "../../../runtime/local/state/battleLocalGateway";

interface BattleSettlementOverlayProps {
  summary: LocalBattleReturnSummary;
  replayId: string | null;
  onNewMatch: () => void;
}

export function BattleSettlementOverlay({ summary, replayId, onNewMatch }: BattleSettlementOverlayProps) {
  return (
    <div className="arena-shell__overlay arena-shell__overlay--settled">
      <div className="settlement-board">
        <div className="settlement-board__header">
          <small>SETTLEMENT</small>
          <h2>本局结束</h2>
          <p>
            排名 {summary.placement ? `#${summary.placement}` : "--"} / 得分 {summary.score} / 用时{" "}
            {summary.durationLabel}
          </p>
        </div>

        <div className="settlement-board__stats">
          {summary.settlementCards.slice(0, 3).map((card) => (
            <article key={card.label}>
              <small>{card.label}</small>
              <strong>{card.value}</strong>
            </article>
          ))}
        </div>

        <div className="settlement-board__actions">
          <button
            type="button"
            className="settlement-board__action settlement-board__action--primary"
            onClick={onNewMatch}
          >
            再来一局
          </button>
          <Link className="settlement-board__action" to={replayId ? `/replay/${replayId}` : "/replay"}>
            查看回放
          </Link>
          <Link className="settlement-board__action" to="/rating">
            查看变化
          </Link>
          <Link className="settlement-board__action settlement-board__action--ghost" to="/">
            返回大厅
          </Link>
        </div>
      </div>
    </div>
  );
}
