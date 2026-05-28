export type ForumTopicId = string;
export type ForumReplyId = string;
export type ForumTitle = string;
export type ForumBody = string;
export type ForumTag = string;
export type ForumScore = number;
export type ForumReplyCount = number;
export type ForumVoterKey = string;
export type ForumVoteChoiceDto = "up" | "down";

export interface ForumVotes {
  valuesByVoter: Record<ForumVoterKey, ForumVoteChoiceDto>;
}

export interface ForumReplyRecord {
  id: ForumReplyId;
  authorHandle: string;
  body: ForumBody;
  createdAt: number;
  votes: ForumVotes;
}

export interface ForumTopicRecord {
  id: ForumTopicId;
  title: ForumTitle;
  body: ForumBody;
  tag: ForumTag;
  authorHandle: string;
  createdAt: number;
  updatedAt: number;
  replies: ForumReplyRecord[];
  votes: ForumVotes;
}

export interface ForumCreateTopicApiRequestDto {
  title: string;
  body: string;
  tag: string;
  author: string;
}

export interface ForumCreateReplyApiRequestDto {
  body: string;
  author: string;
}

export interface ForumVoteApiRequestDto {
  author: string;
  vote?: ForumVoteChoiceDto | null;
}

export interface ForumReplyResponseDto {
  id: string;
  author: string;
  body: string;
  publishedAt: number;
  viewerVote: ForumVoteChoiceDto | null;
  score: number;
}

export interface ForumTopicResponseDto {
  id: string;
  title: string;
  author: string;
  excerpt: string;
  tag: string;
  replies: number;
  updatedAt: number;
  createdAt: number;
  body: string;
  replyItems: ForumReplyResponseDto[];
  viewerVote: ForumVoteChoiceDto | null;
  score: number;
}

export interface ForumTopicListResponseDto {
  topics: ForumTopicResponseDto[];
}

export interface ForumTopicWrapperResponseDto {
  topic: ForumTopicResponseDto;
}
