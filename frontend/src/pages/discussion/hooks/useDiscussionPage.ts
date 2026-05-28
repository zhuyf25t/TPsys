import { useEffect, useMemo, useState } from "react";
import {
  fetchDiscussionSummaries,
  submitDiscussionReportRemote,
  submitDiscussionTopicRemote,
  submitDiscussionVoteRemote,
  type DiscussionSummary,
  type DiscussionVote
} from "../../../apis/forum/forumGateway";

export type { DiscussionSummary, DiscussionVote } from "../../../apis/forum/forumGateway";

const DEFAULT_TAG = "战术讨论";
const DISCUSSION_TAGS = ["战术讨论", "组队招募", "版本反馈"] as const;

export interface DiscussionPageState {
  body: string;
  closeReport: () => void;
  composerOpen: boolean;
  discussionSummaries: DiscussionSummary[];
  discussionTags: readonly string[];
  error: string | null;
  loading: boolean;
  openComposer: () => void;
  openReport: (topic: DiscussionSummary) => void;
  refresh: () => Promise<void>;
  replyCount: number;
  reportBody: string;
  reportMessage: string | null;
  reportTarget: DiscussionSummary | null;
  resetComposer: () => void;
  setBody: (body: string) => void;
  setReportBody: (body: string) => void;
  setTag: (tag: string) => void;
  setTitle: (title: string) => void;
  submitReport: () => Promise<void>;
  submitTopic: () => Promise<void>;
  submitVote: (topic: DiscussionSummary, vote: DiscussionVote) => Promise<void>;
  tag: string;
  title: string;
}

/** 中文名称：论坛列表页Hook。游戏职责：封装话题加载、发帖、投票和举报副作用。 */
export function useDiscussionPage(): DiscussionPageState {
  const [discussionSummaries, setDiscussionSummaries] = useState<DiscussionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [tag, setTag] = useState(DEFAULT_TAG);
  const [body, setBody] = useState("");
  const [reportTarget, setReportTarget] = useState<DiscussionSummary | null>(null);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTopics(): Promise<void> {
      setLoading(true);
      try {
        const items = await fetchDiscussionSummaries();
        if (cancelled) {
          return;
        }

        setDiscussionSummaries(items);
        setError(null);
      } catch {
        if (!cancelled) {
          setError("论坛加载失败，请稍后重试。");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadTopics();

    return () => {
      cancelled = true;
    };
  }, []);

  const replyCount = useMemo(() => discussionSummaries.reduce((sum, topic) => sum + topic.replies, 0), [discussionSummaries]);

  async function refresh(): Promise<void> {
    setLoading(true);
    try {
      const items = await fetchDiscussionSummaries();
      setDiscussionSummaries(items);
      setError(null);
    } catch {
      setError("论坛加载失败，请稍后重试。");
    } finally {
      setLoading(false);
    }
  }

  function resetComposer(): void {
    setTitle("");
    setTag(DEFAULT_TAG);
    setBody("");
    setComposerOpen(false);
  }

  async function submitTopic(): Promise<void> {
    const created = await submitDiscussionTopicRemote({ title, tag, body });
    if (!created) {
      setError("发帖失败，请稍后重试。");
      return;
    }

    resetComposer();
    await refresh();
  }

  async function submitVote(topic: DiscussionSummary, vote: DiscussionVote): Promise<void> {
    const updated = await submitDiscussionVoteRemote(topic.id, topic.viewerVote === vote ? null : vote);
    if (!updated && topic.viewerVote !== vote) {
      setError("投票失败，请稍后重试。");
      return;
    }

    await refresh();
  }

  function openComposer(): void {
    setComposerOpen(true);
  }

  function openReport(topic: DiscussionSummary): void {
    setReportTarget(topic);
    setReportBody("");
    setReportMessage(null);
  }

  function closeReport(): void {
    setReportTarget(null);
    setReportBody("");
    setReportMessage(null);
  }

  async function submitReport(): Promise<void> {
    if (!reportTarget) {
      return;
    }

    const notification = await submitDiscussionReportRemote(reportTarget, reportBody);
    if (!notification) {
      setReportMessage("通知管理员失败。");
      return;
    }

    setReportBody("");
    setReportMessage(notification.ok ? "已通知管理员处理。" : "通知管理员失败。");
  }

  return {
    body,
    closeReport,
    composerOpen,
    discussionSummaries,
    discussionTags: DISCUSSION_TAGS,
    error,
    loading,
    openComposer,
    openReport,
    refresh,
    replyCount,
    reportBody,
    reportMessage,
    reportTarget,
    resetComposer,
    setBody,
    setReportBody,
    setTag,
    setTitle,
    submitReport,
    submitTopic,
    submitVote,
    tag,
    title
  };
}
