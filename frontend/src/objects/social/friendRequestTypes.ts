import type { MailItemResponseDto } from "../mail/mailTypes";

export type FriendRequestId = string;
export type FriendRequestStatusDto = "pending" | "accepted" | "rejected";
export type FriendRequestDecisionDto = "accepted" | "rejected";

export interface FriendRequestRecord {
  id: FriendRequestId;
  sourceHandle: string;
  targetHandle: string;
  createdAt: number;
  status: FriendRequestStatusDto;
  respondedAt: number | null;
}

export interface FriendRequestCreateApiRequestDto {
  sourceHandle?: string;
  targetHandle?: string;
}

export interface FriendRequestRespondApiRequestDto {
  requestId?: string;
  actorHandle?: string;
  decision?: FriendRequestDecisionDto;
}

export interface FriendRequestResponseDto {
  id: string;
  sourceHandle: string;
  targetHandle: string;
  createdAt: number;
  status: FriendRequestStatusDto;
  respondedAt: number | null;
}

export interface FriendRequestListResponseDto {
  requests: FriendRequestResponseDto[];
}

export interface FriendRequestCreateResponseDto {
  created: boolean;
  alreadySent: boolean;
  request: FriendRequestResponseDto;
  mail: MailItemResponseDto | null;
}

export interface FriendRequestRespondResponseDto {
  request: FriendRequestResponseDto;
  mail: MailItemResponseDto | null;
}
