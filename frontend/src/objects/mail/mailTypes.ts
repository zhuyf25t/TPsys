export type MailId = string;
export type MailFriendRequestId = string;
export type GovernanceMailActorHandle = string;
export type GovernanceMailTargetPath = string;
export type GovernanceMailTargetLabel = string;

export type MailKindDto = "system" | "battle" | "reward" | "friend" | "governance";
export type MailFriendRequestStatusDto = "pending" | "accepted" | "rejected";
export type MailReadStateDto = "unread" | "read";
export type MailImportanceDto = "normal" | "important";

export interface GovernanceMailMetadata {
  actorHandle: GovernanceMailActorHandle;
  targetPath: GovernanceMailTargetPath;
  targetLabel: GovernanceMailTargetLabel;
}

export interface FriendRequestMailMetadata {
  requestId: MailFriendRequestId;
  status: MailFriendRequestStatusDto;
  sourceHandle: string;
}

export interface MailRecord {
  id: MailId;
  ownerHandle: string;
  kind: MailKindDto;
  subject: string;
  excerpt: string;
  senderLabel: string;
  readState: MailReadStateDto;
  importance: MailImportanceDto;
  createdAt: number;
  sourceBattleId?: string;
  sourcePath?: string;
  sourceLabel?: string;
  governanceMetadata?: GovernanceMailMetadata;
  friendRequestMetadata?: FriendRequestMailMetadata;
}

export interface MailReadApiRequestDto {
  ownerHandle?: string;
  mailId?: string;
}

export interface MailListApiRequestDto {
  ownerHandle?: string;
}

export interface MailItemResponseDto {
  id: string;
  ownerHandle: string;
  kind: MailKindDto;
  subject: string;
  excerpt: string;
  senderLabel: string;
  unread: boolean;
  important: boolean;
  createdAt: number;
  sourceBattleId?: string;
  sourcePath?: string;
  sourceLabel?: string;
  friendRequestId?: string;
  friendRequestStatus?: MailFriendRequestStatusDto;
  friendRequestSourceHandle?: string;
  governanceActorHandle?: string;
  governanceTargetPath?: string;
  governanceTargetLabel?: string;
}

export interface MailListResponseDto {
  mails: MailItemResponseDto[];
}

export interface MailReadResponseDto {
  ok: boolean;
}
