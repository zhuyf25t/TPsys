import { buildApiUrl, normalizeApiBase } from "../api/apiUrl";

export interface ReplayCommentApiRecord {
  id: string;
  replayId: string;
  authorHandle: string;
  body: string;
  createdAt: number;
}

interface ReplayCommentsResponse {
  comments?: unknown;
  comment?: unknown;
}

const REPLAY_API_BASE = normalizeApiBase(
  import.meta.env.VITE_REPLAY_API_BASE ?? import.meta.env.VITE_BATTLE_API_BASE ?? "",
  "/api"
);
const REQUEST_TIMEOUT_MS = 5_000;

export async function loadReplayComments(replayId: string): Promise<ReplayCommentApiRecord[] | null> {
  const normalizedReplayId = replayId.trim();
  if (typeof window === "undefined" || !normalizedReplayId || !REPLAY_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(buildApiUrl(REPLAY_API_BASE, `/replay/catalog/${encodeURIComponent(normalizedReplayId)}/comments?limit=50`), {
      method: "GET",
      cache: "no-store",
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    const payload = (await response.json().catch(() => null)) as ReplayCommentsResponse | null;
    const rawComments = Array.isArray(payload?.comments) ? payload.comments : [];
    return rawComments
      .map((comment) => normalizeReplayComment(comment))
      .filter((comment): comment is ReplayCommentApiRecord => comment !== null);
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function createReplayComment(input: {
  replayId: string;
  authorHandle: string;
  body: string;
}): Promise<ReplayCommentApiRecord | null> {
  const replayId = input.replayId.trim();
  const authorHandle = input.authorHandle.trim();
  const body = input.body.trim();
  if (typeof window === "undefined" || !replayId || !authorHandle || !body || !REPLAY_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(buildApiUrl(REPLAY_API_BASE, `/replay/catalog/${encodeURIComponent(replayId)}/comments`), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      cache: "no-store",
      signal: controller.signal,
      body: JSON.stringify({
        authorHandle,
        body
      })
    });

    if (!response.ok) {
      return null;
    }

    const payload = (await response.json().catch(() => null)) as ReplayCommentsResponse | null;
    const normalizedComment = normalizeReplayComment(payload?.comment);
    if (normalizedComment) {
      return normalizedComment;
    }

    const rawComments = Array.isArray(payload?.comments) ? payload.comments : [];
    for (const comment of rawComments) {
      const normalized = normalizeReplayComment(comment);
      if (normalized) {
        return normalized;
      }
    }

    return null;
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeReplayComment(value: unknown): ReplayCommentApiRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const comment = value as Partial<Record<keyof ReplayCommentApiRecord, unknown>>;
  if (!hasFields(comment, REPLAY_COMMENT_FIELDS)) {
    return null;
  }

  const id = readString(comment.id);
  const replayId = readString(comment.replayId);
  const authorHandle = readString(comment.authorHandle);
  const body = readString(comment.body);
  const createdAt = readNumber(comment.createdAt);
  if (!id || !replayId || !authorHandle || body === null || createdAt === null) {
    return null;
  }

  return {
    id,
    replayId,
    authorHandle,
    body,
    createdAt
  };
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

const REPLAY_COMMENT_FIELDS = ["id", "replayId", "authorHandle", "body", "createdAt"] satisfies (keyof ReplayCommentApiRecord)[];
