import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Link, useParams } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
import { getLocalFeedback, saveLocalFeedback, type LocalFeedbackEntry } from "../features/governance/localFeedbackStore";
import {
  buildReplayExportArtifactFromPlayback,
  buildReplayRoomInsights,
  getReplaySummaryById,
  hasMeaningfulReplayFrames,
  loadReplayPlaybackById,
  parseReplayPlayersLine,
  type ReplaySummary,
  type ReplayTimelineMoment
} from "../features/replay/replayGateway";
import { getReplayDisplayTitle } from "../features/replay/replayPresentation";
import type { ReplayPlayback } from "../features/replay/replayTypes";
import { ReplayViewer } from "../features/replay/ReplayViewer";
import { ShellLayout } from "../shared/ui/ShellLayout";
import { UserActionDot } from "../shared/ui/UserActionDot";

type ReplayLoadState = "loading" | "ready" | "summary" | "missing";

export function ReplayDetailPage() {
  const { id } = useParams<{ id: string }>();
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const localSummary = useMemo(() => getReplaySummaryById(id ?? ""), [id]);
  const [loadedReplay, setLoadedReplay] = useState<ReplayPlayback | null>(null);
  const [loadState, setLoadState] = useState<ReplayLoadState>(id ? "loading" : "missing");
  const [visibleTimeline, setVisibleTimeline] = useState<ReplayTimelineMoment[]>([]);
  const [comments, setComments] = useState<LocalFeedbackEntry[]>([]);
  const [commentBody, setCommentBody] = useState("");

  useEffect(() => {
    let active = true;
    let retryTimer: number | null = null;

    if (!id) {
      setLoadedReplay(null);
      setLoadState("missing");
      return;
    }

    setLoadedReplay(null);
    setLoadState("loading");
    setVisibleTimeline([]);

    const attemptLoad = (attempt = 0): void => {
      void loadReplayPlaybackById(id).then((replay) => {
        if (!active) {
          return;
        }

        if (replay) {
          setLoadedReplay(replay);
          setLoadState(hasMeaningfulReplayFrames(replay.frames) ? "ready" : "summary");
          return;
        }

        if (attempt < 5) {
          retryTimer = window.setTimeout(() => attemptLoad(attempt + 1), 180 + attempt * 220);
          return;
        }

        setLoadState(localSummary ? "summary" : "missing");
      });
    };

    attemptLoad();

    return () => {
      active = false;
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
      }
    };
  }, [id, localSummary]);

  useEffect(() => {
    setComments(id ? getLocalFeedback(id, "comment") : []);
  }, [id]);

  const replay = useMemo(
    () => (loadedReplay ? mergeReplaySummaryRatings(loadedReplay, localSummary) : localSummary ? summaryToReplay(localSummary) : null),
    [loadedReplay, localSummary]
  );
  const insights = useMemo(() => (replay ? buildReplayRoomInsights(replay) : null), [replay]);
  const hasPlayableFrames = Boolean(replay && hasMeaningfulReplayFrames(replay.frames));
  const exportArtifact = useMemo(
    () => (loadedReplay && replay ? buildReplayExportArtifactFromPlayback(replay) : undefined),
    [loadedReplay, replay]
  );
  const handleTimelineChange = useCallback((items: ReplayTimelineMoment[]) => {
    setVisibleTimeline((current) => (areTimelineMomentsEqual(current, items) ? current : items));
  }, []);
  const timelineItems = visibleTimeline.slice(-5);
  const resultRows = useMemo(
    () => buildResultRows(replay, insights?.roster ?? [], authUser?.handle),
    [authUser?.handle, insights?.roster, replay]
  );

  const submitComment = (): void => {
    if (!id || !authUser || !commentBody.trim()) {
      return;
    }

    const saved = saveLocalFeedback({
      replayId: id,
      kind: "comment",
      author: authUser.handle,
      body: commentBody
    });

    if (saved) {
      setComments(getLocalFeedback(id, "comment"));
      setCommentBody("");
    }
  };

  if (!replay) {
    return (
      <ShellLayout title="回放详情" subtitle="没有找到这一局战报。">
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有找到这一局回放</h3>
          <p>完成一局新的 battle 后，这里会优先读取完整战报 JSON。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/replay">
              返回回放库
            </Link>
          </div>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title="回放详情" subtitle={loadState === "ready" ? "本地逐帧播放" : "战报摘要"} hidePageHeader>
      <section className="replay-detail replay-detail--tv">
        <header className="replay-detail__header replay-detail__header--room replay-detail__header--compact">
          <div className="replay-detail__titleblock">
            <div className="replay-detail__title-row">
              <h3>{getReplayDisplayTitle(replay)}</h3>
              <span className={`pill replay-detail__state replay-detail__state--${hasPlayableFrames ? "playable" : "summary"}`}>
                {hasPlayableFrames ? "可播放" : "仅摘要"}
              </span>
            </div>
            <div className="pill-row replay-detail__header-actions">
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

            <section className="replay-live-events" aria-label="当前播放事件">
              {timelineItems.length ? (
                timelineItems.map((moment) => (
                  <article key={`${moment.timeLabel}-${moment.title}`} className={`replay-live-events__item replay-timeline__item--${moment.tone}`}>
                    <span>{moment.timeLabel}</span>
                    <strong>{moment.title}</strong>
                    <p>{moment.detail}</p>
                  </article>
                ))
              ) : (
                <p>当前播放点没有事件。</p>
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
                <span className="pill">{hasPlayableFrames ? `${replay.frames.length} 帧` : "仅摘要"}</span>
                <span className="pill">{formatDuration(replay.durationMs)}</span>
              </div>
            </section>

            <section className="detail-card replay-comments">
              <h3>评论</h3>
              <div className="replay-comments__list">
                {comments.length ? (
                  comments.map((comment) => (
                    <article key={comment.id} className="replay-comments__item">
                      <div className="replay-comments__meta">
                        <strong>@{comment.author}</strong>
                        <span>{formatLocalTime(comment.createdAt)}</span>
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
                  <textarea value={commentBody} onChange={(event) => setCommentBody(event.target.value)} placeholder="写一条本地评论" />
                  <button type="button" className="button-link button-link--primary" onClick={submitComment} disabled={!commentBody.trim()}>
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
    </ShellLayout>
  );
}

function summaryToReplay(summary: ReplaySummary): ReplayPlayback {
  return {
    id: summary.id,
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

function mergeReplaySummaryRatings(playback: ReplayPlayback, summary: ReplaySummary | undefined): ReplayPlayback {
  if (!summary) {
    return playback;
  }

  return {
    ...playback,
    ratingBefore: playback.ratingBefore ?? summary.ratingBefore,
    ratingAfter: playback.ratingAfter ?? summary.ratingAfter,
    ratingDelta: playback.ratingDelta ?? summary.ratingDelta
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

  if (replay.placement != null) {
    const placementIndex = replay.placement - 1;
    if (placementIndex >= 0 && placementIndex < rows.length) {
      return placementIndex;
    }
  }

  const normalizedHandle = currentHandle?.trim().toLowerCase();
  if (normalizedHandle) {
    const handleIndex = rows.findIndex((row) => row.name.trim().toLowerCase() === normalizedHandle);
    if (handleIndex >= 0) {
      return handleIndex;
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
