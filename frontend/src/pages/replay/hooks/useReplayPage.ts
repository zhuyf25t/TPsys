import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { submitGovernanceReviewNotification } from "../../../apis/governance/governanceGateway";
import { getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import { loadReplaySummaries, type ReplaySummary } from "../../../apis/replay/replayGateway";
import { getReplayDisplayTitle } from "../../../objects/replay/replayPresentation";

export type { ReplaySummary } from "../../../apis/replay/replayGateway";

export type ReplayFeedbackKind = "proposal" | "report";
export type ReplayFeedbackModalState = { replay: ReplaySummary; kind: ReplayFeedbackKind } | null;
export type ReplayLibraryFilter = "all" | "summary" | "playable";

export interface ReplayPageState {
  canSubmitFeedback: boolean;
  closeFeedback: () => void;
  feedbackBody: string;
  feedbackMessage: string | null;
  feedbackModal: ReplayFeedbackModalState;
  feedbackSending: boolean;
  filteredReplays: ReplaySummary[];
  openFeedback: (replay: ReplaySummary, kind: ReplayFeedbackKind) => void;
  replayCount: number;
  replayFilter: ReplayLibraryFilter;
  replayFilterCounts: Record<ReplayLibraryFilter, number>;
  sendFeedback: () => Promise<void>;
  setFeedbackBody: (body: string) => void;
  setReplayFilter: (filter: ReplayLibraryFilter) => void;
}

/** 中文名称：回放页Hook。游戏职责：封装回放加载、筛选状态和治理反馈副作用。 */
export function useReplayPage(): ReplayPageState {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const [replaySummaries, setReplaySummaries] = useState<ReplaySummary[]>([]);
  const [replayFilter, setReplayFilter] = useState<ReplayLibraryFilter>("all");
  const [feedbackModal, setFeedbackModal] = useState<ReplayFeedbackModalState>(null);
  const [feedbackBody, setFeedbackBody] = useState("");
  const [feedbackSending, setFeedbackSending] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    void loadReplaySummaries().then((remote) => {
      if (!active || remote == null) {
        return;
      }

      setReplaySummaries(remote);
    });

    return () => {
      active = false;
    };
  }, []);

  const sortedReplays = useMemo(() => [...replaySummaries].sort(compareReplayRecency), [replaySummaries]);

  const replayFilterCounts = useMemo(
    () => ({
      all: sortedReplays.length,
      summary: sortedReplays.filter((replay) => !replay.playbackAvailable).length,
      playable: sortedReplays.filter((replay) => replay.playbackAvailable).length
    }),
    [sortedReplays]
  );

  const filteredReplays = useMemo(
    () =>
      sortedReplays.filter((replay) => {
        if (replayFilter === "playable") {
          return replay.playbackAvailable;
        }

        if (replayFilter === "summary") {
          return !replay.playbackAvailable;
        }

        return true;
      }),
    [replayFilter, sortedReplays]
  );

  const openFeedback = (replay: ReplaySummary, kind: ReplayFeedbackKind): void => {
    setFeedbackModal({ replay, kind });
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
    if (!feedbackModal || !authUser) {
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
        targetId: feedbackModal.replay.id,
        targetTitle: getReplayDisplayTitle(feedbackModal.replay),
        targetPath: `/replay/${feedbackModal.replay.id}`,
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

  return {
    canSubmitFeedback: authUser !== null,
    closeFeedback,
    feedbackBody,
    feedbackMessage,
    feedbackModal,
    feedbackSending,
    filteredReplays,
    openFeedback,
    replayCount: sortedReplays.length,
    replayFilter,
    replayFilterCounts,
    sendFeedback,
    setFeedbackBody,
    setReplayFilter
  };
}

function compareReplayRecency(left: ReplaySummary, right: ReplaySummary): number {
  return getReplayRecency(right) - getReplayRecency(left);
}

function getReplayRecency(replay: ReplaySummary): number {
  return Number.isFinite(replay.finishedAt) && replay.finishedAt > 0 ? replay.finishedAt : getReplayTimestamp(replay.id);
}

function getReplayTimestamp(id: string): number {
  const match = id.match(/(\d{10,})/);
  return match ? Number(match[1]) : 0;
}
