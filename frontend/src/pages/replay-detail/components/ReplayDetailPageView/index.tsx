import { Link } from "react-router-dom";
import { ReplayViewer } from "../../../replay/components/ReplayViewer";
import { UserActionDot } from "../../../shared/components/user-action-dot/UserActionDot";
import type { ReplayDetailPageState } from "../../hooks/useReplayDetailPage";
import { getReplayDisplayTitle } from "../../../../objects/replay/replayPresentation";
import { ShellLayout } from "../../../../components/ui/ShellLayout";

/** 中文名称：回放详情视图。游戏职责：渲染回放播放器、结算、评论和治理反馈。 */
export function ReplayDetailPageView({
  canComment,
  canDownloadExport,
  canSubmitFeedback,
  closeFeedback,
  commentBody,
  commentSending,
  comments,
  downloadReplayExport,
  feedbackBody,
  feedbackMessage,
  feedbackModal,
  feedbackSending,
  handleTimelineChange,
  hasPlayableFrames,
  hasReplayFrames,
  loadState,
  openFeedback,
  replay,
  resultRows,
  sendFeedback,
  setCommentBody,
  setFeedbackBody,
  setSideCommentBody,
  setSideCommentTargetId,
  sideCommentBody,
  sideCommentMessage,
  sideCommentSending,
  sideCommentTargetId,
  sideCommentTargets,
  submitComment,
  submitSideComment,
  timelineItems
}: ReplayDetailPageState) {
  if (!replay) {
    const loading = loadState === "loading";
    return (
      <ShellLayout title="回放详情" subtitle={loading ? "正在加载回放" : "没有找到这条回放"} hidePageHeader backTo="/replay" backLabel="返回回放列表">
        <section className="detail-card empty-state empty-state--dense">
          <h3>{loading ? "加载回放中" : "没有找到这条回放"}</h3>
          <p>{loading ? "正在读取完整战报和逐帧回放。" : "完成一局战斗后，这里会优先读取完整战报和逐帧回放。"}</p>
          {!loading ? (
            <div className="cta-row">
              <Link className="button-link button-link--primary" to="/replay">
                返回回放列表
              </Link>
            </div>
          ) : null}
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout
      title="回放详情"
      subtitle={loadState === "ready" ? "可拖动播放" : loadState === "summary" && hasReplayFrames ? "服务端关键帧预览" : "战报摘要"}
      hidePageHeader
      backTo="/replay"
      backLabel="返回回放列表"
    >
      <section className="replay-detail replay-detail--tv">
        <header className="replay-detail__header replay-detail__header--room replay-detail__header--compact">
          <div className="replay-detail__title-row">
            <h3>{getReplayDisplayTitle(replay)}</h3>
            <div className="pill-row replay-detail__header-actions">
              <span className={`pill replay-detail__state replay-detail__state--${hasPlayableFrames ? "playable" : "summary"}`}>
                {hasPlayableFrames ? "可回放" : hasReplayFrames ? "仅关键帧" : "仅摘要"}
              </span>
              <span className="pill">{replay.finishedAtLabel}</span>
              <span className="pill">{replay.modeLabel}</span>
              <span className="pill">{replay.mapLabel}</span>
              <span className="pill">{replay.resultLabel}</span>
              {canDownloadExport ? (
                <button type="button" className="button-link replay-detail__export" onClick={downloadReplayExport}>
                  导出 JSON
                </button>
              ) : null}
            </div>
          </div>
        </header>

        <div className="replay-detail__body replay-detail__body--tv">
          <main className="replay-detail__main">
            <section className="detail-card replay-room__viewer-card replay-room__viewer-card--tv">
              <ReplayViewer replay={replay} onTimelineChange={handleTimelineChange} />
            </section>

            <section className="replay-live-events" aria-label="当前播放附近事件">
              {timelineItems.length ? (
                timelineItems.map((moment) => (
                  <article
                    key={`${moment.timeLabel}-${moment.title}-${moment.detail}`}
                    className={`replay-live-events__item replay-timeline__item--${moment.tone}`}
                    title={`${moment.title} · ${moment.detail}`}
                  >
                    <span className="replay-live-events__time">{moment.timeLabel}</span>
                    <p>{moment.detail}</p>
                  </article>
                ))
              ) : (
                <p className="replay-live-events__empty">当前播放点附近没有事件。</p>
              )}
            </section>
          </main>

          <aside className="replay-detail__aside replay-detail__aside--tv">
            <section className="detail-card replay-result-board">
              <h3>本局结果</h3>
              <div className="replay-ranking-table">
                <div className="replay-ranking-table__head">
                  <span>玩家</span>
                  <span>旧 rating</span>
                  <span>新 rating</span>
                  <span>变化</span>
                </div>
                {resultRows.map((row) => (
                  <div key={row.key} className={`replay-ranking-table__row${row.isCurrentPlayer ? " replay-ranking-table__row--current" : ""}`}>
                    <strong>
                      <span className="replay-ranking-table__rank">{row.rankLabel}</span>{" "}
                      {isLinkableHandle(row.name) ? (
                        <span className="user-handle-row user-handle-row--inline">
                          <Link to={profilePath(row.name)}>{row.name}</Link>
                          <UserActionDot handle={row.name} />
                        </span>
                      ) : (
                        row.name
                      )}
                    </strong>
                    <span>{row.oldRating}</span>
                    <span>{row.newRating}</span>
                    <span>{row.delta}</span>
                  </div>
                ))}
              </div>
              <div className="pill-row replay-result-board__meta">
                <span className="pill">{replay.resultLabel}</span>
                <span className="pill">{hasPlayableFrames ? `${replay.frames.length} 帧` : hasReplayFrames ? `服务端关键帧 ${replay.frames.length}` : "仅摘要"}</span>
                <span className="pill">{formatDuration(replay.durationMs)}</span>
              </div>
            </section>

            <section className="detail-card replay-comments">
              <h3>评论</h3>
              <div className="cta-row">
                <button type="button" className="button-link button-link--primary" onClick={() => openFeedback("proposal")}>
                  建议
                </button>
                <button type="button" className="button-link button-link--primary" onClick={() => openFeedback("report")}>
                  举报
                </button>
              </div>
              <div className="replay-comments__list">
                {comments.length ? (
                  comments.map((comment) => (
                    <article key={comment.id} className="replay-comments__item">
                      <div className="replay-comments__meta">
                        <strong>@{comment.authorHandle}</strong>
                        <time dateTime={new Date(comment.createdAt).toISOString()}>{formatLocalTime(comment.createdAt)}</time>
                      </div>
                      <p>{comment.body}</p>
                    </article>
                  ))
                ) : (
                  <p className="replay-comments__empty">暂无真实评论。</p>
                )}
              </div>
              {canComment ? (
                <div className="replay-comments__composer">
                  <textarea value={commentBody} onChange={(event) => setCommentBody(event.target.value)} placeholder="写一条评论" />
                  <button type="button" className="button-link button-link--primary" onClick={submitComment} disabled={!commentBody.trim() || commentSending}>
                    发送
                  </button>
                </div>
              ) : (
                <p className="replay-comments__empty">游客模式不能评论，登录后可留下记录。</p>
              )}
            </section>

            <section className="detail-card replay-comments replay-comments--side-channel">
              <h3>旁路评论</h3>
              <p className="replay-comments__empty">观看本局时，也可以给另一局回放留下队友批注。</p>
              {sideCommentTargets.length ? (
                <>
                  <label className="replay-comments__target">
                    <span>目标回放</span>
                    <select value={sideCommentTargetId} onChange={(event) => setSideCommentTargetId(event.target.value)}>
                      {sideCommentTargets.map((target) => (
                        <option key={target.id} value={target.id}>
                          {getReplayDisplayTitle(target)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className="replay-comments__composer">
                    <textarea value={sideCommentBody} onChange={(event) => setSideCommentBody(event.target.value)} placeholder="给另一局写一条评论" />
                    <button
                      type="button"
                      className="button-link button-link--primary"
                      onClick={submitSideComment}
                      disabled={!sideCommentBody.trim() || sideCommentSending}
                    >
                      {sideCommentSending ? "发送中..." : "发送"}
                    </button>
                  </div>
                  {sideCommentMessage ? <p className="replay-comments__empty">{sideCommentMessage}</p> : null}
                </>
              ) : (
                <p className="replay-comments__empty">至少需要两条回放后才能旁路评论。</p>
              )}
            </section>
          </aside>
        </div>
      </section>

      {feedbackModal ? (
        <div className="replay-modal" role="dialog" aria-modal="true" aria-label={feedbackModal.kind === "proposal" ? "提交建议" : "提交举报"}>
          <div className="replay-modal__panel">
            <button type="button" className="replay-modal__close" onClick={closeFeedback} aria-label="关闭">
              ×
            </button>
            <h3>{feedbackModal.kind === "proposal" ? "建议" : "举报"}</h3>
            <p>{getReplayDisplayTitle(replay)}</p>
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

function formatDuration(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function formatLocalTime(timestamp: number): string {
  return new Intl.DateTimeFormat("zh-CN", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "2-digit"
  }).format(timestamp);
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}

function isLinkableHandle(handle: string): boolean {
  return handle.trim().length > 0 && handle !== "--";
}
