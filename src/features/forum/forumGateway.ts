import { getCurrentAuthHandle } from "../auth/authGateway";
import { saveLocalFeedback } from "../governance/localFeedbackStore";
import {
  createDiscussionReply,
  createDiscussionTopic,
  getDiscussionTopicById,
  getDiscussionTopicVote,
  getDiscussionTopics,
  setDiscussionTopicVote,
  type LocalDiscussionReply,
  type LocalDiscussionTopic,
  type LocalDiscussionVote
} from "./localDiscussionStore";

export type DiscussionVote = LocalDiscussionVote;

export interface DiscussionReply {
  id: string;
  author: string;
  body: string;
  publishedAt: string;
}

export interface DiscussionSummary {
  id: string;
  title: string;
  author: string;
  excerpt: string;
  tag: string;
  replies: number;
  updatedAt: string;
  body: string;
  replyItems: DiscussionReply[];
  viewerVote: DiscussionVote | null;
}

export interface CreateDiscussionTopicInput {
  title: string;
  body: string;
  tag: string;
  author?: string;
}

export interface CreateDiscussionReplyInput {
  body: string;
  author?: string;
}

export function getDiscussionSummaries(): DiscussionSummary[] {
  return getDiscussionTopics().map((topic) => toDiscussionSummary(topic));
}

export function getDiscussionSummaryById(id: string): DiscussionSummary | undefined {
  const topic = getDiscussionTopicById(id);
  return topic ? toDiscussionSummary(topic) : undefined;
}

export function submitDiscussionTopic(input: CreateDiscussionTopicInput): DiscussionSummary | null {
  const topic = createDiscussionTopic({
    title: input.title,
    body: input.body,
    tag: input.tag,
    author: input.author ?? getCurrentAuthHandle()
  });

  return topic ? toDiscussionSummary(topic) : null;
}

export function submitDiscussionReply(
  topicId: string,
  input: CreateDiscussionReplyInput
): DiscussionSummary | null {
  const topic = createDiscussionReply(topicId, {
    body: input.body,
    author: input.author ?? getCurrentAuthHandle()
  });

  return topic ? toDiscussionSummary(topic) : null;
}

export function submitDiscussionVote(topicId: string, vote: DiscussionVote): DiscussionVote | null {
  if (!getDiscussionTopicById(topicId)) {
    return null;
  }

  return setDiscussionTopicVote(topicId, vote);
}

export function submitDiscussionReport(topicId: string, body: string, author?: string): boolean {
  if (!getDiscussionTopicById(topicId)) {
    return false;
  }

  return Boolean(
    saveLocalFeedback({
      replayId: `forum:${topicId}`,
      kind: "report",
      author: author ?? getCurrentAuthHandle(),
      body
    })
  );
}

function toDiscussionSummary(topic: LocalDiscussionTopic): DiscussionSummary {
  return {
    id: topic.id,
    title: topic.title,
    author: topic.author,
    excerpt: buildExcerpt(topic.body),
    tag: topic.tag,
    replies: topic.replies.length,
    updatedAt: formatRelativeTime(topic.updatedAt),
    body: topic.body,
    replyItems: topic.replies.map((reply) => toDiscussionReply(reply)),
    viewerVote: getDiscussionTopicVote(topic.id)
  };
}

function toDiscussionReply(reply: LocalDiscussionReply): DiscussionReply {
  return {
    id: reply.id,
    author: reply.author,
    body: reply.body,
    publishedAt: formatRelativeTime(reply.createdAt)
  };
}

function buildExcerpt(body: string): string {
  const trimmed = body.trim();
  if (trimmed.length <= 90) {
    return trimmed;
  }

  return `${trimmed.slice(0, 90)}…`;
}

function formatRelativeTime(timestamp: number): string {
  const deltaMs = Date.now() - timestamp;
  const deltaMinutes = Math.max(0, Math.floor(deltaMs / 60000));

  if (deltaMinutes < 1) {
    return "刚刚";
  }

  if (deltaMinutes < 60) {
    return `${deltaMinutes} 分钟前`;
  }

  const deltaHours = Math.floor(deltaMinutes / 60);
  if (deltaHours < 24) {
    return `${deltaHours} 小时前`;
  }

  const deltaDays = Math.floor(deltaHours / 24);
  if (deltaDays < 7) {
    return `${deltaDays} 天前`;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(timestamp);
}
