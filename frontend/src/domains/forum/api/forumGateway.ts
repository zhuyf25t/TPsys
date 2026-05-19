import { getCurrentAuthHandle } from "../../identity/api/authGateway";
import { submitGovernanceReviewNotification, type GovernanceReviewNotificationResult } from "../../governance/api/governanceGateway";
import { createForumReply, createForumTopic, loadForumTopicByIdResult, loadForumTopics, setForumReplyVote, setForumVote, type ForumReplyApiRecord, type ForumTopicApiRecord } from "./forumApi";

export type DiscussionVote = "up" | "down" | null;

export interface DiscussionReply {
  id: string;
  author: string;
  body: string;
  publishedAt: string;
  viewerVote: DiscussionVote;
  score: number;
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
  viewerVote: DiscussionVote;
  score: number;
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

type ForumTopicSnapshot = ForumTopicApiRecord;

let cachedForumTopics: ForumTopicSnapshot[] | null = null;
let cachedForumRefreshPromise: Promise<void> | null = null;

void refreshForumCache();

/** 中文名：获取discussionsummaries（getDiscussionSummaries）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function getDiscussionSummaries(): DiscussionSummary[] {
  return getCachedForumTopics().map(toDiscussionSummary);
}

/** 中文名：获取discussion摘要by标识（getDiscussionSummaryById）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function getDiscussionSummaryById(id: string): DiscussionSummary | undefined {
  const topic = getCachedForumTopicById(id);
  return topic ? toDiscussionSummary(topic) : undefined;
}

export async function fetchDiscussionSummaries(): Promise<DiscussionSummary[]> {
  const remoteTopics = await loadForumTopics(getCurrentAuthHandle());
  if (remoteTopics) {
    cachedForumTopics = remoteTopics;
    return remoteTopics.map(toDiscussionSummary);
  }

  return [];
}

export async function fetchDiscussionSummaryById(id: string): Promise<DiscussionSummary | null> {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return null;
  }

  const remoteTopic = await loadForumTopicByIdResult(normalizedId, getCurrentAuthHandle());
  if (remoteTopic.status === "found") {
    upsertCachedForumTopic(remoteTopic.topic);
    return toDiscussionSummary(remoteTopic.topic);
  }

  return null;
}

export async function submitDiscussionTopicRemote(input: CreateDiscussionTopicInput): Promise<DiscussionSummary | null> {
  const created = await createForumTopic({
    title: input.title,
    body: input.body,
    tag: input.tag,
    author: input.author ?? getCurrentAuthHandle()
  });

  if (!created) {
    return null;
  }

  upsertCachedForumTopic(created);
  return toDiscussionSummary(created);
}

export async function submitDiscussionReplyRemote(
  topicId: string,
  input: CreateDiscussionReplyInput
): Promise<DiscussionSummary | null> {
  const updated = await createForumReply(topicId, {
    body: input.body,
    author: input.author ?? getCurrentAuthHandle()
  });

  if (!updated) {
    return null;
  }

  upsertCachedForumTopic(updated);
  return toDiscussionSummary(updated);
}

export async function submitDiscussionVoteRemote(topicId: string, vote: DiscussionVote): Promise<DiscussionVote | null> {
  const updated = await setForumVote(topicId, {
    author: getCurrentAuthHandle(),
    vote
  });

  if (!updated) {
    return null;
  }

  upsertCachedForumTopic(updated);
  return updated.viewerVote;
}

export async function submitDiscussionReplyVoteRemote(
  topicId: string,
  replyId: string,
  vote: DiscussionVote
): Promise<DiscussionVote | null> {
  const updated = await setForumReplyVote(topicId, replyId, {
    author: getCurrentAuthHandle(),
    vote
  });

  if (!updated) {
    return null;
  }

  upsertCachedForumTopic(updated);
  const updatedReply = updated.replyItems.find((reply) => reply.id === replyId);
  return updatedReply?.viewerVote ?? null;
}

/** 中文名：提交discussiontopic（submitDiscussionTopic）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionTopic(_input: CreateDiscussionTopicInput): null {
  return null;
}

/** 中文名：提交discussionreply（submitDiscussionReply）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionReply(_topicId: string, _input: CreateDiscussionReplyInput): null {
  return null;
}

/** 中文名：提交discussionvote（submitDiscussionVote）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionVote(_topicId: string, _vote: DiscussionVote): null {
  return null;
}

/** 中文名：提交discussionreplyvote（submitDiscussionReplyVote）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionReplyVote(_topicId: string, _replyId: string, _vote: DiscussionVote): null {
  return null;
}

/** 中文名：提交discussionreport（submitDiscussionReport）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionReport(topicId: string, body: string, author?: string): boolean {
  return Boolean(topicId.trim() && body.trim() && (author ?? getCurrentAuthHandle()).trim());
}

export async function submitDiscussionReportRemote(
  topic: Pick<DiscussionSummary, "id" | "title">,
  body: string,
  author?: string
): Promise<GovernanceReviewNotificationResult | null> {
  const actorHandle = author ?? getCurrentAuthHandle();
  if (!topic.id.trim() || !body.trim() || !actorHandle.trim()) {
    return null;
  }

  return submitGovernanceReviewNotification({
    actorHandle,
    kind: "discussion_report",
    targetType: "discussion",
    targetId: topic.id,
    targetTitle: topic.title,
    targetPath: `/discussion/${encodeURIComponent(topic.id)}`,
    body
  });
}

/** 中文名：提交discussionreplyreport（submitDiscussionReplyReport）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function submitDiscussionReplyReport(
  topicId: string,
  replyId: string,
  body: string,
  author?: string
): boolean {
  return Boolean(topicId.trim() && replyId.trim() && body.trim() && (author ?? getCurrentAuthHandle()).trim());
}

export async function submitDiscussionReplyReportRemote(
  topic: Pick<DiscussionSummary, "id" | "title">,
  reply: Pick<DiscussionReply, "id" | "author">,
  body: string,
  author?: string
): Promise<GovernanceReviewNotificationResult | null> {
  const actorHandle = author ?? getCurrentAuthHandle();
  if (!topic.id.trim() || !reply.id.trim() || !body.trim() || !actorHandle.trim()) {
    return null;
  }

  return submitGovernanceReviewNotification({
    actorHandle,
    kind: "discussion_report",
    targetType: "discussion",
    targetId: `${topic.id}#${reply.id}`,
    targetTitle: `${topic.title} / @${reply.author} 的评论`,
    targetPath: `/discussion/${encodeURIComponent(topic.id)}#reply-${encodeURIComponent(reply.id)}`,
    body
  });
}

function getCachedForumTopics(): ForumTopicSnapshot[] {
  return cachedForumTopics ? [...cachedForumTopics].sort((left, right) => right.updatedAt - left.updatedAt) : [];
}

function getCachedForumTopicById(id: string): ForumTopicSnapshot | undefined {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return undefined;
  }

  return getCachedForumTopics().find((topic) => topic.id === normalizedId);
}

function upsertCachedForumTopic(topic: ForumTopicSnapshot): void {
  const nextTopics = cachedForumTopics ? cachedForumTopics.filter((entry) => entry.id !== topic.id) : [];
  nextTopics.unshift(topic);
  cachedForumTopics = nextTopics.sort((left, right) => right.updatedAt - left.updatedAt);
}

async function refreshForumCache(): Promise<void> {
  if (cachedForumRefreshPromise) {
    return cachedForumRefreshPromise;
  }

  cachedForumRefreshPromise = (async () => {
    const remoteTopics = await loadForumTopics(getCurrentAuthHandle()).catch(() => null);
    if (remoteTopics) {
      cachedForumTopics = remoteTopics;
    }
  })().finally(() => {
    cachedForumRefreshPromise = null;
  });

  return cachedForumRefreshPromise;
}

function toDiscussionSummary(topic: ForumTopicApiRecord): DiscussionSummary {
  return {
    id: topic.id,
    title: topic.title,
    author: topic.author,
    excerpt: topic.excerpt,
    tag: topic.tag,
    replies: topic.replies,
    updatedAt: formatRelativeTime(topic.updatedAt),
    body: topic.body,
    replyItems: topic.replyItems.map(toDiscussionReply),
    viewerVote: topic.viewerVote,
    score: topic.score
  };
}

function toDiscussionReply(reply: ForumReplyApiRecord): DiscussionReply {
  return {
    id: reply.id,
    author: reply.author,
    body: reply.body,
    publishedAt: formatRelativeTime(reply.publishedAt),
    viewerVote: reply.viewerVote,
    score: reply.score
  };
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
