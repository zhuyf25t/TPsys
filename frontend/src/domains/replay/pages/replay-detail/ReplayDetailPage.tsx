import { useCallback, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
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
import { cn } from "../../../../shared/ui/classNames";

type ReplayLoadState = "loading" | "ready" | "summary" | "missing";
type FeedbackModalState = { kind: "proposal" | "report" } | null;

const primaryButton =
  "inline-flex h-10 items-center justify-center rounded-md bg-emerald-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50";
const dangerButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-rose-200 bg-rose-50 px-4 text-sm font-semibold text-rose-700 transition hover:bg-rose-100";
const pillClassName = "inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600";

/** 中文名称：回放详情页。游戏职责：复现战斗过程、展示结算、评论和治理反馈。 */
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
  const resultRows = useMemo(() => buildResultRows(replay, insights?.roster ?? [], ratingHandle ?? authUser?.handle), [authUser?.handle, insights?.roster, ratingHandle, replay]);

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
        <EmptyState title="没有找到这条回放" body="完成一局战斗后，这里会优先读取完整战报和逐帧回放。">
          <Link className={primaryButton} to="/replay">
            返回回放列表
          </Link>
        </EmptyState>
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
      <section className="mx-auto flex w-full max-w-7xl flex-col gap-5">
        <header className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <h3 className="text-2xl font-semibold text-slate-950">{getReplayDisplayTitle(replay)}</h3>
            <div className="flex flex-wrap gap-2">
              <span className={cn(pillClassName, hasPlayableFrames ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700")}>
                {hasPlayableFrames ? "可回放" : hasReplayFrames ? "仅关键帧" : "仅摘要"}
              </span>
              <span className={pillClassName}>{replay.finishedAtLabel}</span>
              <span className={pillClassName}>{replay.modeLabel}</span>
              <span className={pillClassName}>{replay.mapLabel}</span>
              <span className={pillClassName}>{replay.resultLabel}</span>
              {exportArtifact ? (
                <button type="button" className={secondaryButton} onClick={() => downloadReplayJson(exportArtifact)}>
                  导出 JSON
                </button>
              ) : null}
            </div>
          </div>
        </header>

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
          <main className="flex min-w-0 flex-col gap-4">
            <section className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
              <ReplayViewer replay={replay} onTimelineChange={handleTimelineChange} />
            </section>

            <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5" aria-label="当前播放附近事件">
              {timelineItems.length ? (
                timelineItems.map((moment) => (
                  <article key={`${moment.timeLabel}-${moment.title}-${moment.detail}`} className={cn("rounded-lg border p-3 text-sm shadow-sm", timelineToneClass(moment.tone))} title={`${moment.title} | ${moment.detail}`}>
                    <span className="text-xs font-semibold text-slate-500">{moment.timeLabel}</span>
                    <p className="mt-1 line-clamp-2 text-slate-700">{moment.detail}</p>
                  </article>
                ))
              ) : (
                <p className="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-sm text-slate-500">当前播放点附近没有事件。</p>
              )}
            </section>
          </main>

          <aside className="flex min-w-0 flex-col gap-5">
            <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-950">本局结果</h3>
              <div className="mt-4 overflow-hidden rounded-lg border border-slate-200">
                <div className="grid grid-cols-[1fr_80px_80px_72px] gap-2 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-500">
                  <span>玩家</span>
                  <span>旧 rating</span>
                  <span>新 rating</span>
                  <span>变化</span>
                </div>
                <div className="divide-y divide-slate-100">
                  {resultRows.map((row) => (
                    <div key={row.key} className={cn("grid grid-cols-[1fr_80px_80px_72px] gap-2 px-3 py-3 text-sm", row.isCurrentPlayer ? "bg-emerald-50 text-emerald-950" : "text-slate-700")}>
                      <strong className="min-w-0">
                        <span className="mr-1 text-slate-500">{row.rankLabel}</span>
                        {isLinkableHandle(row.name) ? (
                          <span className="inline-flex min-w-0 items-center gap-1">
                            <Link className="truncate hover:text-emerald-700" to={profilePath(row.name)}>
                              {row.name}
                            </Link>
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
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <span className={pillClassName}>{replay.resultLabel}</span>
                <span className={pillClassName}>{hasPlayableFrames ? `${replay.frames.length} 帧` : hasReplayFrames ? `服务端关键帧 ${replay.frames.length}` : "仅摘要"}</span>
                <span className={pillClassName}>{formatDuration(replay.durationMs)}</span>
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-lg font-semibold text-slate-950">评论</h3>
                <div className="flex gap-2">
                  <button type="button" className={secondaryButton} onClick={() => openFeedback("proposal")}>
                    建议
                  </button>
                  <button type="button" className={dangerButton} onClick={() => openFeedback("report")}>
                    举报
                  </button>
                </div>
              </div>
              <div className="mt-4 flex flex-col gap-3">
                {comments.length ? (
                  comments.map((comment) => (
                    <article key={comment.id} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                        <strong>@{comment.authorHandle}</strong>
                        <time dateTime={new Date(comment.createdAt).toISOString()}>{formatLocalTime(comment.createdAt)}</time>
                      </div>
                      <p className="mt-2 text-sm leading-6 text-slate-700">{comment.body}</p>
                    </article>
                  ))
                ) : (
                  <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500">暂无真实评论。</p>
                )}
              </div>
              {authUser ? (
                <div className="mt-4 flex flex-col gap-3">
                  <textarea
                    className="min-h-28 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20"
                    value={commentBody}
                    onChange={(event) => setCommentBody(event.target.value)}
                    placeholder="写一条评论"
                  />
                  <button type="button" className={primaryButton} onClick={submitComment} disabled={!commentBody.trim() || commentSending}>
                    发送
                  </button>
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-500">游客模式不能评论，登录后可留下记录。</p>
              )}
            </section>
          </aside>
        </div>
      </section>

      {feedbackModal ? (
        <Modal title={feedbackModal.kind === "proposal" ? "建议" : "举报"} ariaLabel={feedbackModal.kind === "proposal" ? "提交建议" : "提交举报"} onClose={closeFeedback}>
          <p className="text-sm font-semibold text-slate-700">{getReplayDisplayTitle(replay)}</p>
          {authUser ? (
            <div className="mt-4 flex flex-col gap-4">
              <textarea
                className="min-h-32 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20"
                value={feedbackBody}
                onChange={(event) => setFeedbackBody(event.target.value)}
                placeholder={feedbackModal.kind === "proposal" ? "写一条建议" : "描述问题"}
              />
              {feedbackMessage ? <span className="text-sm font-semibold text-emerald-700">{feedbackMessage}</span> : null}
              <button
                type="button"
                className={primaryButton}
                onClick={() => {
                  void sendFeedback();
                }}
                disabled={!feedbackBody.trim() || feedbackSending}
              >
                {feedbackSending ? "提交中..." : "提交"}
              </button>
            </div>
          ) : (
            <span className="mt-4 block text-sm font-semibold text-rose-700">{feedbackMessage ?? "请先登录后再提交。"}</span>
          )}
        </Modal>
      ) : null}
    </ShellLayout>
  );
}

function EmptyState({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <section className="mx-auto flex max-w-xl flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
      <h3 className="text-xl font-semibold text-slate-950">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-slate-600">{body}</p>
      {children ? <div className="mt-5">{children}</div> : null}
    </section>
  );
}

function Modal({ title, ariaLabel, onClose, children }: { title: string; ariaLabel: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6" role="dialog" aria-modal="true" aria-label={ariaLabel}>
      <div className="relative max-h-[90vh] w-full max-w-xl overflow-auto rounded-lg bg-white p-6 shadow-2xl">
        <button
          type="button"
          className="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-lg font-semibold text-slate-500 hover:bg-slate-50"
          onClick={onClose}
          aria-label="关闭"
        >
          x
        </button>
        <h3 className="pr-10 text-xl font-semibold text-slate-950">{title}</h3>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}

function timelineToneClass(tone: ReplayTimelineMoment["tone"]): string {
  switch (tone) {
    case "success":
      return "border-emerald-200 bg-emerald-50";
    case "warning":
      return "border-amber-200 bg-amber-50";
    case "danger":
      return "border-rose-200 bg-rose-50";
    default:
      return "border-slate-200 bg-white";
  }
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

function buildResultRows(replay: ReplayPlayback | null, roster: Array<{ heroId: string; displayName: string; placementLabel?: string }>, currentHandle?: string) {
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

function resolveCurrentPlayerRowIndex(replay: ReplayPlayback | null, rows: Array<{ name: string }>, currentHandle?: string): number {
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

function hasReplayRating(replay: ReplayPlayback | null): replay is ReplayPlayback & { ratingBefore: number; ratingAfter: number; ratingDelta: number } {
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
