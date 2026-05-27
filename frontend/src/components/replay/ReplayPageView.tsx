import { Link } from "react-router-dom";
import type { ReplayLibraryFilter, ReplayPageState, ReplaySummary } from "../../hooks/replay-page/useReplayPage";
import { getReplayDisplayTitle } from "../../objects/replay/replayPresentation";
import { ShellLayout } from "../../shared/ui/ShellLayout";

const REPLAY_FILTERS: Array<{ id: ReplayLibraryFilter; label: string }> = [
  { id: "all", label: "总数" },
  { id: "summary", label: "仅摘要" },
  { id: "playable", label: "可播放" }
];

/** 中文名称：回放页视图。游戏职责：渲染回放摘要、时间线和治理反馈入口。 */
export function ReplayPageView({
  canSubmitFeedback,
  closeFeedback,
  feedbackBody,
  feedbackMessage,
  feedbackModal,
  feedbackSending,
  filteredReplays,
  openFeedback,
  replayCount,
  replayFilter,
  replayFilterCounts,
  sendFeedback,
  setFeedbackBody,
  setReplayFilter
}: ReplayPageState) {
  return (
    <ShellLayout title="回放列表" subtitle="本地战报和治理建议队列" hidePageHeader>
      <section className="replay-library__lead">
        <div className="replay-library__heading">
          <p className="replay-library__eyebrow">回放列表</p>
          <h3>回放列表</h3>
          <p className="replay-library__meta">
            总计 {replayFilterCounts.all} 条 · 仅摘要 {replayFilterCounts.summary} 条 · 可播放 {replayFilterCounts.playable} 条
          </p>
        </div>

        <div className="replay-library__filters" role="group" aria-label="回放筛选">
          {REPLAY_FILTERS.map((filter) => (
            <button
              key={filter.id}
              type="button"
              className={`replay-filter-chip${replayFilter === filter.id ? " replay-filter-chip--active" : ""}`}
              aria-pressed={replayFilter === filter.id}
              onClick={() => setReplayFilter(filter.id)}
            >
              <span>{filter.label}</span>
              <strong>{replayFilterCounts[filter.id]}</strong>
            </button>
          ))}
        </div>
      </section>

      {replayCount === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>暂无回放</h3>
          <p>完成一局战斗后，这里会显示本地摘要和评审数据。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle?new=1">
              进入战斗
            </Link>
          </div>
        </section>
      ) : filteredReplays.length === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有符合筛选的回放</h3>
          <p>试试其他筛选，或者返回完整列表。</p>
        </section>
      ) : (
        <section className="replay-library replay-library--rows">
          {filteredReplays.map((replay) => (
            <article key={replay.id} className="replay-card replay-card--row">
              <div className="replay-card__cover replay-card__cover--thumb">
                {replay.thumbnailDataUrl ? (
                  <img src={replay.thumbnailDataUrl} alt={`${replay.title} 缩略图`} className="replay-card__image" />
                ) : (
                  <BattleThumbFallback label={replay.coverLabel} />
                )}
              </div>

              <div className="replay-card__body">
                <div className="replay-card__header">
                  <div className="replay-card__title-row">
                    <strong>{getReplayDisplayTitle(replay)}</strong>
                    <span className="replay-card__time">{replay.finishedAtLabel}</span>
                  </div>
                  <p className="replay-card__players" aria-label="玩家排名摘要">
                    {getRankedPlayerSummary(replay)}
                  </p>

                  <div className="replay-card__meta">
                    <span className="pill replay-card__mode">{replay.modeLabel}</span>
                    {getReplayTags(replay).map((tag) => (
                      <span key={`${replay.id}-${tag}`} className="pill">
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="replay-card__actions">
                <Link className="button-link button-link--primary replay-card__view" to={`/replay/${replay.id}`}>
                  查看回放
                </Link>
                <button type="button" className="replay-feedback replay-feedback--proposal" onClick={() => openFeedback(replay, "proposal")}>
                  提议
                </button>
                <button type="button" className="replay-feedback replay-feedback--report" onClick={() => openFeedback(replay, "report")}>
                  举报
                </button>
              </div>
            </article>
          ))}
        </section>
      )}

      {feedbackModal ? (
        <div className="replay-modal" role="dialog" aria-modal="true" aria-label={feedbackModal.kind === "proposal" ? "提交建议" : "提交举报"}>
          <div className="replay-modal__panel">
            <button type="button" className="replay-modal__close" onClick={closeFeedback} aria-label="关闭">
              ×
            </button>
            <h3>{feedbackModal.kind === "proposal" ? "建议" : "举报"}</h3>
            <p>{getReplayDisplayTitle(feedbackModal.replay)}</p>
            {canSubmitFeedback ? (
              <>
                <textarea
                  value={feedbackBody}
                  onChange={(event) => setFeedbackBody(event.target.value)}
                  placeholder={feedbackModal.kind === "proposal" ? "写一条建议" : "描述问题"}
                />
                {feedbackMessage ? <span className="replay-modal__thanks">{feedbackMessage}</span> : null}
                <button
                  type="button"
                  className="button-link button-link--primary"
                  onClick={() => {
                    void sendFeedback();
                  }}
                  disabled={!feedbackBody.trim() || feedbackSending}
                >
                  {feedbackSending ? "提交中..." : "提交"}
                </button>
              </>
            ) : (
              <span className="replay-modal__thanks">{feedbackMessage ?? "请先登录后再提交。"}</span>
            )}
          </div>
        </div>
      ) : null}
    </ShellLayout>
  );
}

function getRankedPlayerSummary(replay: ReplaySummary): string {
  const names = parseReplayPlayersLine(replay.playersLine);
  if (names.length === 0) {
    return "暂无排名摘要";
  }

  const topPlayers = names.slice(0, 4).map((player, index) => `#${index + 1} ${player}`);
  if (names.length > 4) {
    topPlayers.push(`+${names.length - 4}`);
  }

  return topPlayers.join(" · ");
}

function getReplayTags(replay: ReplaySummary): string[] {
  return Array.from(new Set([replay.mapLabel, replay.resultLabel].filter(Boolean)));
}

function parseReplayPlayersLine(playersLine: string): string[] {
  if (!playersLine.trim()) {
    return [];
  }

  return playersLine
    .replace(/\s+vs\s+/gi, " | ")
    .replace(/\s*(?:[|,/;]|\uFF0C|\u3001|\uFF1B|->|--|-|\u2014)\s*/g, " | ")
    .split(/\s*\|\s*/g)
    .map((name) => name.trim())
    .filter(Boolean);
}

function BattleThumbFallback({ label }: { label: string }) {
  return (
    <div className="replay-thumb-fallback">
      <span>{label}</span>
      <i className="replay-thumb-fallback__hero replay-thumb-fallback__hero--one" />
      <i className="replay-thumb-fallback__hero replay-thumb-fallback__hero--two" />
      <i className="replay-thumb-fallback__spark replay-thumb-fallback__spark--one" />
      <i className="replay-thumb-fallback__spark replay-thumb-fallback__spark--two" />
    </div>
  );
}
