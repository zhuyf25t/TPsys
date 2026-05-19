const STORAGE_KEY = "slay-demo.local-discussion.v1";
const VOTES_STORAGE_KEY = "slay-demo.local-discussion-votes.v1";
const REPLY_VOTES_STORAGE_KEY = "slay-demo.local-discussion-reply-votes.v1";

export type LocalDiscussionVote = "up" | "down";

export interface LocalDiscussionReply {
  id: string;
  author: string;
  body: string;
  createdAt: number;
}

export interface LocalDiscussionTopic {
  id: string;
  title: string;
  author: string;
  body: string;
  tag: string;
  createdAt: number;
  updatedAt: number;
  replies: LocalDiscussionReply[];
}

interface LocalDiscussionState {
  version: 1;
  topics: LocalDiscussionTopic[];
}

interface LocalDiscussionVoteState {
  version: 1;
  votes: Record<string, LocalDiscussionVote>;
}

interface LocalDiscussionReplyVoteState {
  version: 1;
  votes: Record<string, LocalDiscussionVote>;
}

interface CreateDiscussionTopicInput {
  title: string;
  body: string;
  tag: string;
  author: string;
}

interface CreateDiscussionReplyInput {
  body: string;
  author: string;
}

export function getDiscussionTopics(): LocalDiscussionTopic[] {
  return [...readState().topics].sort((left, right) => right.updatedAt - left.updatedAt);
}

export function getDiscussionTopicById(id: string): LocalDiscussionTopic | undefined {
  return readState().topics.find((topic) => topic.id === id);
}

export function getDiscussionTopicVote(topicId: string): LocalDiscussionVote | null {
  const normalizedTopicId = topicId.trim();
  if (!normalizedTopicId) {
    return null;
  }

  return readVoteState().votes[normalizedTopicId] ?? null;
}

export function setDiscussionTopicVote(topicId: string, vote: LocalDiscussionVote): LocalDiscussionVote | null {
  const normalizedTopicId = topicId.trim();
  if (!normalizedTopicId) {
    return null;
  }

  const state = readVoteState();
  const nextVotes = { ...state.votes };
  const nextVote = nextVotes[normalizedTopicId] === vote ? null : vote;

  if (nextVote) {
    nextVotes[normalizedTopicId] = nextVote;
  } else {
    delete nextVotes[normalizedTopicId];
  }

  writeVoteState({
    version: 1,
    votes: nextVotes
  });

  return nextVote;
}

export function getDiscussionReplyVote(replyId: string): LocalDiscussionVote | null {
  const normalizedReplyId = replyId.trim();
  if (!normalizedReplyId) {
    return null;
  }

  return readReplyVoteState().votes[normalizedReplyId] ?? null;
}

export function setDiscussionReplyVote(replyId: string, vote: LocalDiscussionVote): LocalDiscussionVote | null {
  const normalizedReplyId = replyId.trim();
  if (!normalizedReplyId) {
    return null;
  }

  const state = readReplyVoteState();
  const nextVotes = { ...state.votes };
  const nextVote = nextVotes[normalizedReplyId] === vote ? null : vote;

  if (nextVote) {
    nextVotes[normalizedReplyId] = nextVote;
  } else {
    delete nextVotes[normalizedReplyId];
  }

  writeReplyVoteState({
    version: 1,
    votes: nextVotes
  });

  return nextVote;
}

export function createDiscussionTopic(input: CreateDiscussionTopicInput): LocalDiscussionTopic | null {
  const title = input.title.trim();
  const body = input.body.trim();
  const tag = input.tag.trim();

  if (!title || !body || !tag) {
    return null;
  }

  const now = Date.now();
  const topic: LocalDiscussionTopic = {
    id: `topic-${now}`,
    title,
    body,
    tag,
    author: input.author.trim() || "Player-1",
    createdAt: now,
    updatedAt: now,
    replies: []
  };

  const state = readState();
  writeState({
    version: 1,
    topics: [topic, ...state.topics].slice(0, 100)
  });

  return topic;
}

export function createDiscussionReply(
  topicId: string,
  input: CreateDiscussionReplyInput
): LocalDiscussionTopic | null {
  const body = input.body.trim();
  if (!body) {
    return null;
  }

  const state = readState();
  const topicIndex = state.topics.findIndex((topic) => topic.id === topicId);
  if (topicIndex < 0) {
    return null;
  }

  const now = Date.now();
  const reply: LocalDiscussionReply = {
    id: `reply-${now}`,
    author: input.author.trim() || "Player-1",
    body,
    createdAt: now
  };

  const topic = state.topics[topicIndex];
  const nextTopic: LocalDiscussionTopic = {
    ...topic,
    updatedAt: now,
    replies: [...topic.replies, reply]
  };

  const topics = [...state.topics];
  topics.splice(topicIndex, 1);
  topics.unshift(nextTopic);

  writeState({
    version: 1,
    topics
  });

  return nextTopic;
}

export function getDiscussionActivitySummary(): {
  topicCount: number;
  replyCount: number;
  lastUpdatedAt: number | null;
} {
  const topics = readState().topics;
  const replyCount = topics.reduce((sum, topic) => sum + topic.replies.length, 0);
  const lastUpdatedAt = topics.reduce<number | null>((latest, topic) => {
    if (latest === null || topic.updatedAt > latest) {
      return topic.updatedAt;
    }
    return latest;
  }, null);

  return {
    topicCount: topics.length,
    replyCount,
    lastUpdatedAt
  };
}

function readState(): LocalDiscussionState {
  if (typeof window === "undefined") {
    return { version: 1, topics: [] };
  }

  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return { version: 1, topics: [] };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<LocalDiscussionState>;
    return {
      version: 1,
      topics: Array.isArray(parsed.topics) ? (parsed.topics as LocalDiscussionTopic[]) : []
    };
  } catch {
    return { version: 1, topics: [] };
  }
}

function writeState(state: LocalDiscussionState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function readVoteState(): LocalDiscussionVoteState {
  if (typeof window === "undefined") {
    return { version: 1, votes: {} };
  }

  const raw = window.localStorage.getItem(VOTES_STORAGE_KEY);
  if (!raw) {
    return { version: 1, votes: {} };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<LocalDiscussionVoteState>;
    return {
      version: 1,
      votes: normalizeVotes(parsed.votes)
    };
  } catch {
    return { version: 1, votes: {} };
  }
}

function writeVoteState(state: LocalDiscussionVoteState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(VOTES_STORAGE_KEY, JSON.stringify(state));
}

function readReplyVoteState(): LocalDiscussionReplyVoteState {
  if (typeof window === "undefined") {
    return { version: 1, votes: {} };
  }

  const raw = window.localStorage.getItem(REPLY_VOTES_STORAGE_KEY);
  if (!raw) {
    return { version: 1, votes: {} };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<LocalDiscussionReplyVoteState>;
    return {
      version: 1,
      votes: normalizeVotes(parsed.votes)
    };
  } catch {
    return { version: 1, votes: {} };
  }
}

function writeReplyVoteState(state: LocalDiscussionReplyVoteState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(REPLY_VOTES_STORAGE_KEY, JSON.stringify(state));
}

function normalizeVotes(input: unknown): Record<string, LocalDiscussionVote> {
  if (!input || typeof input !== "object") {
    return {};
  }

  return Object.entries(input).reduce<Record<string, LocalDiscussionVote>>((votes, [topicId, vote]) => {
    if (vote === "up" || vote === "down") {
      votes[topicId] = vote;
    }

    return votes;
  }, {});
}
