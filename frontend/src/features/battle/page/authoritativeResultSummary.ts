import type { LocalBattleReturnSummary } from "../local/battleLocalGateway";
import type { BackendBattleResultRecord } from "../results/battleResultsApi";

export function toAuthoritativeResultSummary(record: BackendBattleResultRecord): LocalBattleReturnSummary {
  return {
    outcome: "finished",
    score: record.score,
    placement: record.placement,
    durationLabel: formatDuration(record.durationMs),
    ratingDeltaLabel: formatRatingDelta(record.ratingDelta),
    resultLine: `${record.resultLabel} | ${record.finishedAtLabel}`,
    highlightLine: record.highlightLine,
    nextStepLabel: record.timelineHint,
    settlementCards: [
      {
        label: "本局名次",
        value: record.placement ? `第 ${record.placement} 名` : "已完成",
        detail: record.modeLabel
      },
      {
        label: "结算得分",
        value: `${record.score}`,
        detail: record.highlightLine
      },
      {
        label: "评分变化",
        value: formatRatingDelta(record.ratingDelta),
        detail: `当前评分 ${record.ratingAfter}`
      },
      {
        label: "结算玩家",
        value: record.displayName,
        detail: record.playersLine
      }
    ],
    touchpoints: [
      {
        label: "查看回放",
        path: `/replay/${record.battleId}`,
        detail: record.timelineHint
      },
      {
        label: "查看评分",
        path: "/rating",
        detail: `评分 ${record.ratingBefore} -> ${record.ratingAfter}`
      },
      {
        label: "查看主页",
        path: `/profile/${encodeURIComponent(record.handle)}`,
        detail: record.displayName
      }
    ]
  };
}

function formatDuration(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function formatRatingDelta(delta: number): string {
  if (delta > 0) {
    return `+${delta}`;
  }

  return `${delta}`;
}
