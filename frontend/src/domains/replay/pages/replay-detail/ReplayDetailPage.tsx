import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../../../identity/api/authGateway";
import { submitGovernanceReviewNotification } from "../../../governance/api/governanceGateway";
import {
  buildReplayExportArtifactFromPlayback,
  buildReplayRoomInsights,
  getReplaySummaryById,
  hasMeaningfulReplayFrames,
  loadReplayPlaybackById,
  parseReplayPlayersLine,
  type ReplaySummary,
  type ReplayTimelineMoment
} from "../../api/replayGateway";
import { createReplayComment, loadReplayComments, type ReplayCommentApiRecord } from "../../api/replayCommentsApi";
import { getReplayDisplayTitle } from "../../objects/replayPresentation";
import type { ReplayPlayback } from "../../objects/replayTypes";
import { ReplayViewer } from "../../components/ReplayViewer";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";

type ReplayLoadState = "loading" | "ready" | "summary" | "missing";
type FeedbackModalState = { kind: "proposal" | "report" } | null;

/** 中文名：回放detailpage（ReplayDetailPage）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function ReplayDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const ratingHandle = searchParams.get("handle")?.trim() || undefined;
  const [loadedReplay, setLoadedReplay] = useState<ReplayPlayback | null>(null);
  const [summaryReplay, setSummaryReplay] = useState<ReplaySummary | null>(null);
  const [loadState, setLoadState] = useState<ReplayLoadState>(id ? "loading" : "missing");
  const [visibleTimeline, setVisibleTimeline] = useState<ReplayTimelineMoment[]>([]);
  const [comments, setComments] = useState<ReplayCommentApiRecord[]>([]);
  const [commentBody, setCommentBody] = useState("");
  const [commentSending, setCommentSending] = useState(false);
  const [feedbackModal, setFeedbackModal] = useState<FeedbackModalState>(null);
  const [feedbackBody, setFeedbackBody] = useState("");
  const [feedbackSending, setFeedbackSending] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let retryTimer: number | null = null;

    if (!id) {
      setLoadedReplay(null);
      setSummaryReplay(null);
      setLoadState("missing");
      return;
    }

    setLoadedReplay(null);
    setSummaryReplay(null);
    setLoadState("loading");
    setVisibleTimeline([]);

    const attemptLoad = (attempt = 0): void => {
      void loadReplayPlaybackById(id, { ratingHandle }).then((replay) => {
        if (!active) {
          return;
        }

        if (replay) {
          setLoadedReplay(replay);
          setSummaryReplay(getReplaySummaryById(id) ?? null);
          setLoadState(replay.playbackAvailable !== false && hasMeaningfulReplayFrames(replay.frames) ? "ready" : "summary");
          return;
        }

        const summary = getReplaySummaryById(id);
        if (summary) {
          setSummaryReplay(summary);
          setLoadedReplay(null);
          setLoadState("summary");
          return;
        }

        if (attempt < 5) {
          retryTimer = window.setTimeout(() => attemptLoad(attempt + 1), 180 + attempt * 220);
          return;
        }

        setLoadState("missing");
      });
    };

    attemptLoad();

    return () => {
      active = false;
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
      }
    };
  }, [id, ratingHandle]);

  useEffect(() => {
    let active = true;

    if (!id) {
      setComments([]);
      return;
    }

    setComments([]);

    void loadReplayComments(id).then((items) => {
      if (!active) {
        return;
      }

      setComments(items ?? []);
    });

    return () => {
      active = false;
    };
  }, [id]);

  const replay = useMemo(() => mergeReplaySummaryRatings(summaryReplay, loadedReplay), [loadedReplay, summaryReplay]);
  const insights = useMemo(() => (replay ? buildReplayRoomInsights(replay) : null), [replay]);
  const hasReplayFrames = Boolean(replay && replay.frames.length > 0);
  const hasPlayableFrames = Boolean(replay && replay.playbackAvailable !== false && hasMeaningfulReplayFrames(replay.frames));
  const exportArtifact = useMemo(() => (replay ? buildReplayExportArtifactFromPlayback(replay) : undefined), [replay]);
  const handleTimelineChange = useCallback((items: ReplayTimelineMoment[]) => {
    setVisibleTimeline((current) => (areTimelineMomentsEqual(current, items) ? current : items));
  }, []);
  const timelineItems = visibleTimeline.slice(-5);
  const resultRows = useMemo(
    () => buildResultRows(replay, insights?.roster ?? [], ratingHandle ?? authUser?.handle),
    [authUser?.handle, insights?.roster, ratingHandle, replay]
  );

  const submitComment = (): void => {
    if (!id || !authUser || !commentBody.trim()) {
      return;
    }

    setCommentSending(true);
    void createReplayComment({
      replayId: id,
      authorHandle: authUser.handle,
      body: commentBody
    })
      .then((comment) => {
        if (comment) {
          setComments((current) => [...current, comment]);
          setCommentBody("");
          return;
        }

        void loadReplayComments(id).then((items) => {
          setComments(items ?? []);
        });
      })
      .finally(() => {
        setCommentSending(false);
      });
  };

  const openFeedback = (kind: "proposal" | "report"): void => {
    setFeedbackModal({ kind });
    setFeedbackBody("");
    setFeedbackMessage(authUser ? null : "请先登录后再提交。");
    setFeedbackSending(false);
  };

  const closeFeedback = (): void => {
    setFeedbackModal(null);
    setFeedbackBody("");
    setFeedbackSending(false);
    setFeedbackMessage(null);
  };

  const sendFeedback = async (): Promise<void> => {
    if (!feedbackModal || !id || !replay || !authUser) {
      setFeedbackMessage("请先登录后再提交。");
      return;
    }

    const body = feedbackBody.trim();
    if (!body || feedbackSending) {
      return;
    }

    setFeedbackSending(true);
    const notification = await submitGovernanceReviewNotification({
      actorHandle: authUser.handle,
      kind: feedbackModal.kind === "proposal" ? "replay_proposal" : "replay_report",
      targetType: "replay",
      targetId: id,
      targetTitle: getReplayDisplayTitle(replay),
      targetPath: `/replay/${id}`,
      body
    });

    setFeedbackMessage(notification.ok ? "已提交，感谢反馈。" : "提交失败，请稍后再试。");
    if (notification.ok) {
      setFeedbackBody("");
    }
    setFeedbackSending(false);
  };

  if (!replay) {
    return (
      <ShellLayout title="回放详情" subtitle="没有找到这条回放" hidePageHeader backTo="/replay" backLabel="返回回放列表">
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有找到这条回放</h3>
          <p>完成一局战斗后，这里会优先读取完整战报和逐帧回放。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/replay">
              返回回放列表
            </Link>
          </div>
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
              {exportArtifact ? (
                <button type="button" className="button-link replay-detail__export" onClick={() => downloadReplayJson(exportArtifact)}>
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
              {authUser ? (
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
            {authUser ? (
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

function mergeReplaySummaryRatings(summary: ReplaySummary | null, playback: ReplayPlayback | null): ReplayPlayback | null {
  if (playback) {
    if (!summary || summary.localBackendSyncDisabled) {
      return playback;
    }

    return {
      ...playback,
      ratingBefore: playback.ratingBefore ?? summary.ratingBefore,
      ratingAfter: playback.ratingAfter ?? summary.ratingAfter,
      ratingDelta: playback.ratingDelta ?? summary.ratingDelta,
      playbackAvailable: playback.playbackAvailable ?? summary.playbackAvailable
    };
  }

  return summary ? summaryToReplay(summary) : null;
}

function summaryToReplay(summary: ReplaySummary): ReplayPlayback {
  return {
    id: summary.id,
    playbackAvailable: summary.playbackAvailable,
    title: summary.title,
    modeLabel: summary.modeLabel,
    resultLabel: summary.resultLabel,
    finishedAtLabel: summary.finishedAtLabel,
    mapLabel: summary.mapLabel,
    highlightLine: summary.highlightLine,
    timelineHint: summary.timelineHint,
    playersLine: summary.playersLine,
    score: summary.score,
    placement: summary.placement,
    ratingBefore: summary.ratingBefore,
    ratingAfter: summary.ratingAfter,
    ratingDelta: summary.ratingDelta,
    durationMs: summary.durationMs,
    aliveAtEnd: summary.aliveAtEnd,
    thumbnailDataUrl: summary.thumbnailDataUrl,
    frames: []
  };
}

function buildResultRows(
  replay: ReplayPlayback | null,
  roster: Array<{ heroId: string; displayName: string; placementLabel?: string }>,
  currentHandle?: string
) {
  const baseRows =
    roster.length > 0
      ? roster.map((row, index) => ({
          key: row.heroId,
          rankLabel: row.placementLabel ?? `#${index + 1}`,
          name: row.displayName
        }))
      : (replay ? parseReplayPlayersLine(replay.playersLine).slice(0, 6) : []).map((name, index) => ({
          key: `${name}-${index}`,
          rankLabel: `#${index + 1}`,
          name
        }));
  const rows = baseRows.length > 0 ? baseRows : [{ key: "empty", rankLabel: "#-", name: "--" }];
  const currentPlayerIndex = resolveCurrentPlayerRowIndex(replay, rows, currentHandle);
  const hasRating = hasReplayRating(replay);

  return rows.map((row, index) => ({
    ...row,
    oldRating: hasRating && index === currentPlayerIndex ? `${replay.ratingBefore}` : "--",
    newRating: hasRating && index === currentPlayerIndex ? `${replay.ratingAfter}` : "--",
    delta: hasRating && index === currentPlayerIndex ? formatRatingDelta(replay.ratingDelta) : "--",
    isCurrentPlayer: hasRating && index === currentPlayerIndex
  }));
}

function resolveCurrentPlayerRowIndex(
  replay: ReplayPlayback | null,
  rows: Array<{ name: string }>,
  currentHandle?: string
): number {
  if (!replay || rows.length === 0) {
    return -1;
  }

  const normalizedHandle = currentHandle?.trim().toLowerCase();
  if (normalizedHandle) {
    const handleIndex = rows.findIndex((row) => row.name.trim().toLowerCase() === normalizedHandle);
    if (handleIndex >= 0) {
      return handleIndex;
    }
  }

  if (replay.placement != null) {
    const placementIndex = replay.placement - 1;
    if (placementIndex >= 0 && placementIndex < rows.length) {
      return placementIndex;
    }
  }

  return 0;
}

function hasReplayRating(
  replay: ReplayPlayback | null
): replay is ReplayPlayback & { ratingBefore: number; ratingAfter: number; ratingDelta: number } {
  return typeof replay?.ratingBefore === "number" && typeof replay.ratingAfter === "number" && typeof replay.ratingDelta === "number";
}

function formatRatingDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

function areTimelineMomentsEqual(left: ReplayTimelineMoment[], right: ReplayTimelineMoment[]): boolean {
  if (left.length !== right.length) {
    return false;
  }

  return left.every((item, index) => {
    const other = right[index];
    return item.timeLabel === other?.timeLabel && item.title === other.title && item.detail === other.detail && item.tone === other.tone;
  });
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
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(timestamp);
}

function downloadReplayJson(artifact: { filename: string; json: string }): void {
  const blob = new Blob([artifact.json], { type: "application/json;charset=utf-8" });
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = artifact.filename;
  anchor.rel = "noreferrer";
  anchor.click();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 1500);
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}

function isLinkableHandle(handle: string): boolean {
  return handle.trim().length > 0 && handle !== "--";
}
