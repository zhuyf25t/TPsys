import { useEffect, useState } from "react";
import {
  fetchDiscussionSummaryById,
  submitDiscussionReplyRemote,
  submitDiscussionReplyReportRemote,
  submitDiscussionReplyVoteRemote,
  submitDiscussionReportRemote,
  submitDiscussionVoteRemote,
  type DiscussionReply,
  type DiscussionSummary,
  type DiscussionVote
} from "../../api/forum/forumGateway";

export type { DiscussionReply, DiscussionSummary, DiscussionVote } from "../../api/forum/forumGateway";

export type DiscussionReportTarget =
  | { kind: "topic"; topic: DiscussionSummary }
  | { kind: "reply"; topic: DiscussionSummary; reply: DiscussionReply };

export interface DiscussionDetailPageState {
  closeReport: () => void;
  loading: boolean;
  missing: boolean;
  openReplyReport: (reply: DiscussionReply) => void;
  openTopicReport: () => void;
  refreshTopic: () => Promise<void>;
  replyBody: string;
  reportBody: string;
  reportMessage: string | null;
  reportTarget: DiscussionReportTarget | null;
  setReplyBody: (body: string) => void;
  setReportBody: (body: string) => void;
  submitReply: () => Promise<void>;
  submitReplyVote: (reply: DiscussionReply, vote: DiscussionVote) => Promise<void>;
  submitReport: () => Promise<void>;
  submitTopicVote: (vote: DiscussionVote) => Promise<void>;
  topic: DiscussionSummary | null;
}

/** 中文名称：论坛详情页Hook。游戏职责：封装话题详情、回复、投票和举报副作用。 */
export function useDiscussionDetailPage(topicId: string | undefined): DiscussionDetailPageState {
  const [topic, setTopic] = useState<DiscussionSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [replyBody, setReplyBody] = useState("");
  const [reportTarget, setReportTarget] = useState<DiscussionReportTarget | null>(null);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTopic(): Promise<void> {
      setLoading(true);
      setMissing(false);

      const loaded = await fetchDiscussionSummaryById(topicId ?? "");
      if (cancelled) {
        return;
      }

      setTopic(loaded);
      setMissing(!loaded);
      setLoading(false);
    }

    void loadTopic();

    return () => {
      cancelled = true;
    };
  }, [topicId]);

  async function refreshTopic(): Promise<void> {
    const loaded = await fetchDiscussionSummaryById(topicId ?? "");
    setTopic(loaded);
    setMissing(!loaded);
    setLoading(false);
  }

  async function submitReply(): Promise<void> {
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionReplyRemote(topic.id, { body: replyBody });
    if (!updated) {
      setReportMessage("回复失败，请稍后重试。");
      return;
    }

    setReplyBody("");
    setReportMessage(null);
    await refreshTopic();
  }

  async function submitTopicVote(vote: DiscussionVote): Promise<void> {
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionVoteRemote(topic.id, topic.viewerVote === vote ? null : vote);
    if (!updated && topic.viewerVote !== vote) {
      setReportMessage("投票失败，请稍后重试。");
      return;
    }

    await refreshTopic();
  }

  async function submitReplyVote(reply: DiscussionReply, vote: DiscussionVote): Promise<void> {
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionReplyVoteRemote(topic.id, reply.id, reply.viewerVote === vote ? null : vote);
    if (!updated && reply.viewerVote !== vote) {
      setReportMessage("投票失败，请稍后重试。");
      return;
    }

    await refreshTopic();
  }

  function openTopicReport(): void {
    if (topic) {
      setReportTarget({ kind: "topic", topic });
    }
  }

  function openReplyReport(reply: DiscussionReply): void {
    if (topic) {
      setReportTarget({ kind: "reply", topic, reply });
    }
  }

  function closeReport(): void {
    setReportTarget(null);
    setReportBody("");
    setReportMessage(null);
  }

  async function submitReport(): Promise<void> {
    if (!topic || !reportTarget) {
      return;
    }

    const notification =
      reportTarget.kind === "topic"
        ? await submitDiscussionReportRemote(topic, reportBody)
        : await submitDiscussionReplyReportRemote(topic, reportTarget.reply, reportBody);

    if (!notification) {
      setReportMessage("通知管理员失败。");
      return;
    }

    setReportBody("");
    setReportMessage(notification.ok ? "已通知管理员处理。" : "通知管理员失败。");
  }

  return {
    closeReport,
    loading,
    missing,
    openReplyReport,
    openTopicReport,
    refreshTopic,
    replyBody,
    reportBody,
    reportMessage,
    reportTarget,
    setReplyBody,
    setReportBody,
    submitReply,
    submitReplyVote,
    submitReport,
    submitTopicVote,
    topic
  };
}
