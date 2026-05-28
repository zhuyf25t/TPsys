import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { submitGovernanceReviewNotification } from "../../../apis/governance/governanceGateway";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import {
  buildReplayExportArtifactFromPlayback,
  buildReplayRoomInsights,
  getReplaySummaryById,
  hasMeaningfulReplayFrames,
  loadReplayPlaybackById,
  parseReplayPlayersLine,
  type ReplaySummary,
  type ReplayTimelineMoment
} from "../../../apis/replay/replayGateway";
import { createReplayComment, loadReplayComments, type ReplayCommentApiRecord } from "../../../apis/replay/replayCommentsApi";
import { getReplayDisplayTitle } from "../../../objects/replay/replayPresentation";
import type { ReplayPlayback } from "../../../objects/replay/replayTypes";

export type { ReplayCommentApiRecord } from "../../../apis/replay/replayCommentsApi";
export type { ReplayTimelineMoment } from "../../../apis/replay/replayGateway";
export type { ReplayPlayback } from "../../../objects/replay/replayTypes";

export type ReplayLoadState = "loading" | "ready" | "summary" | "missing";
export type ReplayFeedbackKind = "proposal" | "report";
export type ReplayFeedbackModalState = { kind: ReplayFeedbackKind } | null;

export interface ReplayResultRow {
  delta: string;
  isCurrentPlayer: boolean;
  key: string;
  name: string;
  newRating: string;
  oldRating: string;
  rankLabel: string;
}

export interface ReplayDetailPageState {
  canComment: boolean;
  canDownloadExport: boolean;
  canSubmitFeedback: boolean;
  closeFeedback: () => void;
  commentBody: string;
  commentSending: boolean;
  comments: ReplayCommentApiRecord[];
  downloadReplayExport: () => void;
  feedbackBody: string;
  feedbackMessage: string | null;
  feedbackModal: ReplayFeedbackModalState;
  feedbackSending: boolean;
  handleTimelineChange: (items: ReplayTimelineMoment[]) => void;
  hasPlayableFrames: boolean;
  hasReplayFrames: boolean;
  loadState: ReplayLoadState;
  openFeedback: (kind: ReplayFeedbackKind) => void;
  replay: ReplayPlayback | null;
  resultRows: ReplayResultRow[];
  sendFeedback: () => Promise<void>;
  setCommentBody: (body: string) => void;
  setFeedbackBody: (body: string) => void;
  submitComment: () => void;
  timelineItems: ReplayTimelineMoment[];
}

/** 中文名称：回放详情页Hook。游戏职责：封装回放加载、评论、导出和治理反馈副作用。 */
export function useReplayDetailPage(id: string | undefined, ratingHandle: string | undefined): ReplayDetailPageState {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const [loadedReplay, setLoadedReplay] = useState<ReplayPlayback | null>(null);
  const [summaryReplay, setSummaryReplay] = useState<ReplaySummary | null>(null);
  const [loadState, setLoadState] = useState<ReplayLoadState>(id ? "loading" : "missing");
  const [visibleTimeline, setVisibleTimeline] = useState<ReplayTimelineMoment[]>([]);
  const [comments, setComments] = useState<ReplayCommentApiRecord[]>([]);
  const [commentBody, setCommentBody] = useState("");
  const [commentSending, setCommentSending] = useState(false);
  const [feedbackModal, setFeedbackModal] = useState<ReplayFeedbackModalState>(null);
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
      void loadReplayPlaybackById(id, { ratingHandle }).then((replayPlayback) => {
        if (!active) {
          return;
        }

        if (replayPlayback) {
          setLoadedReplay(replayPlayback);
          setSummaryReplay(getReplaySummaryById(id) ?? null);
          setLoadState(replayPlayback.playbackAvailable !== false && hasMeaningfulReplayFrames(replayPlayback.frames) ? "ready" : "summary");
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

  const openFeedback = (kind: ReplayFeedbackKind): void => {
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
    try {
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
    } catch {
      setFeedbackMessage("提交失败，请稍后再试。");
    } finally {
      setFeedbackSending(false);
    }
  };

  const downloadReplayExport = (): void => {
    if (exportArtifact) {
      downloadReplayJson(exportArtifact);
    }
  };

  return {
    canComment: authUser !== null,
    canDownloadExport: Boolean(exportArtifact),
    canSubmitFeedback: authUser !== null,
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
    submitComment,
    timelineItems
  };
}

function mergeReplaySummaryRatings(summary: ReplaySummary | null, playback: ReplayPlayback | null): ReplayPlayback | null {
  if (playback) {
    if (!summary || summary.localBackendSyncDisabled) {
      return playback;
    }

    return {
      ...playback,
      playbackAvailable: playback.playbackAvailable ?? summary.playbackAvailable,
      ratingAfter: playback.ratingAfter ?? summary.ratingAfter,
      ratingBefore: playback.ratingBefore ?? summary.ratingBefore,
      ratingDelta: playback.ratingDelta ?? summary.ratingDelta
    };
  }

  return summary ? summaryToReplay(summary) : null;
}

function summaryToReplay(summary: ReplaySummary): ReplayPlayback {
  return {
    aliveAtEnd: summary.aliveAtEnd,
    durationMs: summary.durationMs,
    finishedAtLabel: summary.finishedAtLabel,
    frames: [],
    highlightLine: summary.highlightLine,
    id: summary.id,
    mapLabel: summary.mapLabel,
    modeLabel: summary.modeLabel,
    placement: summary.placement,
    playbackAvailable: summary.playbackAvailable,
    playersLine: summary.playersLine,
    ratingAfter: summary.ratingAfter,
    ratingBefore: summary.ratingBefore,
    ratingDelta: summary.ratingDelta,
    resultLabel: summary.resultLabel,
    score: summary.score,
    thumbnailDataUrl: summary.thumbnailDataUrl,
    timelineHint: summary.timelineHint,
    title: summary.title
  };
}

function buildResultRows(
  replay: ReplayPlayback | null,
  roster: Array<{ heroId: string; displayName: string; placementLabel?: string }>,
  currentHandle?: string
): ReplayResultRow[] {
  const baseRows =
    roster.length > 0
      ? roster.map((row, index) => ({
          key: row.heroId,
          name: row.displayName,
          rankLabel: row.placementLabel ?? `#${index + 1}`
        }))
      : (replay ? parseReplayPlayersLine(replay.playersLine).slice(0, 6) : []).map((name, index) => ({
          key: `${name}-${index}`,
          name,
          rankLabel: `#${index + 1}`
        }));
  const rows = baseRows.length > 0 ? baseRows : [{ key: "empty", rankLabel: "#-", name: "--" }];
  const currentPlayerIndex = resolveCurrentPlayerRowIndex(replay, rows, currentHandle);
  const hasRating = hasReplayRating(replay);

  return rows.map((row, index) => ({
    ...row,
    delta: hasRating && index === currentPlayerIndex ? formatRatingDelta(replay.ratingDelta) : "--",
    isCurrentPlayer: hasRating && index === currentPlayerIndex,
    newRating: hasRating && index === currentPlayerIndex ? `${replay.ratingAfter}` : "--",
    oldRating: hasRating && index === currentPlayerIndex ? `${replay.ratingBefore}` : "--"
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
