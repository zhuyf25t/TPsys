import { buildApiUrl, normalizeApiBase } from "../../system/api/apiUrl";
import { getCurrentAuthHandle } from "../identity/authGateway";
import type {
  ForumCreateReplyApiRequestDto,
  ForumCreateTopicApiRequestDto,
  ForumReplyResponseDto,
  ForumTopicListResponseDto,
  ForumTopicResponseDto,
  ForumTopicWrapperResponseDto,
  ForumVoteApiRequestDto,
  ForumVoteChoiceDto
} from "../../objects/forum/forumTypes";

export type ForumReplyApiRecord = ForumReplyResponseDto;
export type ForumTopicApiRecord = ForumTopicResponseDto;

type ForumTopicsResponse = Partial<Record<keyof ForumTopicListResponseDto, unknown>>;
type ForumTopicResponse = Partial<Record<keyof ForumTopicWrapperResponseDto, unknown>>;

interface ForumErrorResponse {
  code?: unknown;
}

type ForumTopicApiRecordDto = {
  [Field in keyof ForumTopicApiRecord]?: unknown;
};

type ForumReplyApiRecordDto = {
  [Field in keyof ForumReplyApiRecord]?: unknown;
};

export type ForumTopicLookupResult =
  | { status: "found"; topic: ForumTopicApiRecord }
  | { status: "missing" }
  | { status: "unavailable" };

export type ForumMutationStatus = "ok" | "rejected" | "unavailable";

const FORUM_API_BASE = normalizeApiBase(import.meta.env.VITE_FORUM_API_BASE ?? "", "/api");
const FORUM_API_TIMEOUT_MS = 5_000;

let lastForumMutationStatus: ForumMutationStatus = "ok";

/** 中文名：获取lastforummutation状态（getLastForumMutationStatus）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function getLastForumMutationStatus(): ForumMutationStatus {
  return lastForumMutationStatus;
}

export async function loadForumTopics(viewerHandle?: string): Promise<ForumTopicApiRecord[] | null> {
  if (typeof window === "undefined" || !FORUM_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), FORUM_API_TIMEOUT_MS);

  try {
    const response = await fetch(
      buildApiUrl(FORUM_API_BASE, "/forum/topics", {
        author: viewerHandle?.trim() || getCurrentAuthHandle()
      }),
      {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      }
    );

    if (!response.ok) {
      return isForumListBackendUnavailableStatus(response.status) ? null : [];
    }

    const payload = (await response.json().catch(() => null)) as ForumTopicsResponse | null;
    const rawTopics = Array.isArray(payload?.topics) ? payload.topics : [];

    return rawTopics
      .map((topic) => normalizeForumTopic(topic))
      .filter((topic): topic is ForumTopicApiRecord => topic !== null);
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function loadForumTopicById(
  topicId: string,
  viewerHandle?: string
): Promise<ForumTopicApiRecord | null> {
  const result = await loadForumTopicByIdResult(topicId, viewerHandle);
  return result.status === "found" ? result.topic : null;
}

export async function loadForumTopicByIdResult(
  topicId: string,
  viewerHandle?: string
): Promise<ForumTopicLookupResult> {
  const normalizedTopicId = topicId.trim();
  if (!normalizedTopicId || typeof window === "undefined" || !FORUM_API_BASE) {
    return { status: "unavailable" };
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), FORUM_API_TIMEOUT_MS);

  try {
    const response = await fetch(
      buildApiUrl(FORUM_API_BASE, `/forum/topics/${encodeURIComponent(normalizedTopicId)}`, {
        author: viewerHandle?.trim() || getCurrentAuthHandle()
      }),
      {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      }
    );

    if (!response.ok) {
      if (response.status === 404) {
        return { status: "missing" };
      }

      return isForumLookupBackendUnavailableStatus(response.status) ? { status: "unavailable" } : { status: "missing" };
    }

    const payload = (await response.json().catch(() => null)) as ForumTopicResponse | null;
    const rawTopic = payload?.topic;
    const topic = normalizeForumTopic(rawTopic);
    return topic ? { status: "found", topic } : { status: "unavailable" };
  } catch {
    return { status: "unavailable" };
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function createForumTopic(input: {
  title: string;
  body: string;
  tag: string;
  author?: string;
}): Promise<ForumTopicApiRecord | null> {
  const request: ForumCreateTopicApiRequestDto = {
    title: input.title,
    body: input.body,
    tag: input.tag,
    author: input.author?.trim() || getCurrentAuthHandle()
  };
  return mutateForumTopic("/forum/topics", request);
}

export async function createForumReply(
  topicId: string,
  input: {
    body: string;
    author?: string;
  }
): Promise<ForumTopicApiRecord | null> {
  const normalizedTopicId = topicId.trim();
  if (!normalizedTopicId) {
    return null;
  }

  const request: ForumCreateReplyApiRequestDto = {
    body: input.body,
    author: input.author?.trim() || getCurrentAuthHandle()
  };
  return mutateForumTopic(`/forum/topics/${encodeURIComponent(normalizedTopicId)}/replies`, request);
}

export async function setForumVote(
  topicId: string,
  input: {
    author?: string;
    vote: ForumVoteChoiceDto | null;
  }
): Promise<ForumTopicApiRecord | null> {
  const normalizedTopicId = topicId.trim();
  if (!normalizedTopicId) {
    return null;
  }

  const request: ForumVoteApiRequestDto = {
    author: input.author?.trim() || getCurrentAuthHandle(),
    vote: input.vote
  };
  return mutateForumTopic(`/forum/topics/${encodeURIComponent(normalizedTopicId)}/votes`, request);
}

export async function setForumReplyVote(
  topicId: string,
  replyId: string,
  input: {
    author?: string;
    vote: ForumVoteChoiceDto | null;
  }
): Promise<ForumTopicApiRecord | null> {
  const normalizedTopicId = topicId.trim();
  const normalizedReplyId = replyId.trim();
  if (!normalizedTopicId || !normalizedReplyId) {
    return null;
  }

  const request: ForumVoteApiRequestDto = {
    author: input.author?.trim() || getCurrentAuthHandle(),
    vote: input.vote
  };
  return mutateForumTopic(`/forum/topics/${encodeURIComponent(normalizedTopicId)}/replies/${encodeURIComponent(normalizedReplyId)}/votes`, request);
}

async function mutateForumTopic(
  path: string,
  body: ForumCreateTopicApiRequestDto | ForumCreateReplyApiRequestDto | ForumVoteApiRequestDto
): Promise<ForumTopicApiRecord | null> {
  if (typeof window === "undefined" || !FORUM_API_BASE) {
    lastForumMutationStatus = "unavailable";
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), FORUM_API_TIMEOUT_MS);

  try {
    const response = await fetch(buildApiUrl(FORUM_API_BASE, path), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      cache: "no-store",
      signal: controller.signal,
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const errorCode = await readForumErrorCode(response);
      lastForumMutationStatus = isForumMutationBackendUnavailableStatus(response.status, errorCode) ? "unavailable" : "rejected";
      return null;
    }

    const payload = (await response.json().catch(() => null)) as ForumTopicResponse | null;
    const rawTopic = payload?.topic;
    const topic = normalizeForumTopic(rawTopic);
    lastForumMutationStatus = topic ? "ok" : "rejected";
    return topic;
  } catch {
    lastForumMutationStatus = "unavailable";
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeForumTopic(value: unknown): ForumTopicApiRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const topic = value as ForumTopicApiRecordDto;
  if (!hasFields(topic, FORUM_TOPIC_FIELDS)) {
    return null;
  }

  const id = readString(topic.id);
  const title = readString(topic.title);
  const author = readString(topic.author);
  const excerpt = readString(topic.excerpt);
  const tag = readString(topic.tag);
  const replies = readNumber(topic.replies);
  const updatedAt = readNumber(topic.updatedAt);
  const createdAt = readNumber(topic.createdAt);
  const body = readString(topic.body);
  const replyItems = Array.isArray(topic.replyItems)
    ? topic.replyItems.map((reply) => normalizeForumReply(reply)).filter((reply): reply is ForumReplyApiRecord => reply !== null)
    : null;
  const viewerVote = normalizeForumVote(topic.viewerVote);
  const score = readNumber(topic.score);

  if (
    !id ||
    !title ||
    !author ||
    excerpt === null ||
    !tag ||
    replies === null ||
    updatedAt === null ||
    createdAt === null ||
    !body ||
    replyItems === null ||
    score === null
  ) {
    return null;
  }

  return {
    id,
    title,
    author,
    excerpt,
    tag,
    replies,
    updatedAt,
    createdAt,
    body,
    replyItems,
    viewerVote,
    score
  };
}

function normalizeForumReply(value: unknown): ForumReplyApiRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const reply = value as ForumReplyApiRecordDto;
  if (!hasFields(reply, FORUM_REPLY_FIELDS)) {
    return null;
  }

  const id = readString(reply.id);
  const author = readString(reply.author);
  const body = readString(reply.body);
  const publishedAt = readNumber(reply.publishedAt);
  const viewerVote = normalizeForumVote(reply.viewerVote);
  const score = readNumber(reply.score);
  if (!id || !author || !body || publishedAt === null || score === null) {
    return null;
  }

  return {
    id,
    author,
    body,
    publishedAt,
    viewerVote,
    score
  };
}

function normalizeForumVote(value: unknown): ForumVoteChoiceDto | null {
  if (value === "up" || value === "down") {
    return value;
  }

  return null;
}

async function readForumErrorCode(response: Response): Promise<string> {
  const payload = (await response.json().catch(() => null)) as ForumErrorResponse | null;
  return typeof payload?.code === "string" ? payload.code : "";
}

function isForumMutationBackendUnavailableStatus(status: number, code: string): boolean {
  if (status === 408 || status >= 500) {
    return true;
  }

  return status === 404 && code !== "topic_not_found" && code !== "reply_not_found";
}

function isForumListBackendUnavailableStatus(status: number): boolean {
  return status === 408 || status >= 500 || status === 404;
}

function isForumLookupBackendUnavailableStatus(status: number): boolean {
  return status === 408 || status >= 500 || status === 404;
}

function hasFields<T extends string>(value: Record<string, unknown>, fields: readonly T[]): boolean {
  return fields.every((field) => Object.prototype.hasOwnProperty.call(value, field));
}

function readString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

const FORUM_TOPIC_FIELDS = [
  "id",
  "title",
  "author",
  "excerpt",
  "tag",
  "replies",
  "updatedAt",
  "createdAt",
  "body",
  "replyItems",
  "viewerVote",
  "score"
] satisfies (keyof ForumTopicApiRecord)[];

const FORUM_REPLY_FIELDS = [
  "id",
  "author",
  "body",
  "publishedAt",
  "viewerVote",
  "score"
] satisfies (keyof ForumReplyApiRecord)[];
