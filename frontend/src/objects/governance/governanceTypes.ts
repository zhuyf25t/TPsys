import type { MailImportanceDto, MailKindDto, MailReadStateDto } from "../mail/mailTypes";

export type AdminHandle = string;
export type GovernanceActorHandle = string;
export type GovernanceTargetHandle = string;
export type ContributionAdjustmentId = string;
export type ContributionDelta = number;
export type GovernanceReason = string;
export type GovernanceSourceLabel = string;
export type GovernanceSourcePath = string;
export type GovernanceReviewNotificationId = string;
export type GovernanceReviewTargetId = string;
export type GovernanceReviewTargetTitle = string;
export type GovernanceReviewTargetPath = string;
export type GovernanceReviewBody = string;
export type GovernanceMailSnapshotId = string;

export type GovernanceReviewKindDto =
  | "replay_proposal"
  | "replay_report"
  | "discussion_report"
  | "bot_suggestion";

export type GovernanceReviewTargetTypeDto = "replay" | "discussion" | "bot";

export interface ContributionAdjustmentRecord {
  id: ContributionAdjustmentId;
  actorHandle: AdminHandle;
  targetHandle: GovernanceTargetHandle;
  delta: ContributionDelta;
  reason: GovernanceReason;
  createdAt: number;
  sourceLabel: GovernanceSourceLabel;
  sourcePath: GovernanceSourcePath;
}

export interface GovernanceReviewNotificationRecord {
  id: GovernanceReviewNotificationId;
  actorHandle: GovernanceActorHandle;
  kind: GovernanceReviewKindDto;
  targetType: GovernanceReviewTargetTypeDto;
  targetId: GovernanceReviewTargetId;
  targetTitle: GovernanceReviewTargetTitle;
  targetPath: GovernanceReviewTargetPath;
  body: GovernanceReviewBody;
  createdAt: number;
  mailId: GovernanceMailSnapshotId;
}

export interface GovernanceMailSnapshot {
  id: GovernanceMailSnapshotId;
  ownerHandle: GovernanceTargetHandle;
  kind: MailKindDto;
  subject: string;
  excerpt: string;
  senderLabel: string;
  readState: MailReadStateDto;
  importance: MailImportanceDto;
  createdAt: number;
  governanceMetadata?: GovernanceMailMetadata;
}

export interface GovernanceMailMetadata {
  actorHandle: GovernanceActorHandle;
  targetPath: GovernanceReviewTargetPath;
  targetLabel: GovernanceReviewTargetTitle;
}

export interface ContributionAdjustmentApiRequestDto {
  actorHandle: string;
  targetHandle: string;
  delta: number;
  reason: string;
  sourceLabel: string;
  sourcePath: string;
}

export interface GovernanceReviewNotificationApiRequestDto {
  actorHandle: string;
  kind: string;
  targetType: string;
  targetId: string;
  targetTitle: string;
  targetPath: string;
  body: string;
}

export interface ContributionAdjustmentItemResponseDto {
  id: string;
  actorHandle: string;
  targetHandle: string;
  delta: number;
  reason: string;
  createdAt: number;
  sourceLabel: string;
  sourcePath: string;
}

export interface GovernanceReviewNotificationItemResponseDto {
  id: string;
  actorHandle: string;
  kind: GovernanceReviewKindDto;
  targetType: GovernanceReviewTargetTypeDto;
  targetId: string;
  targetTitle: string;
  targetPath: string;
  body: string;
  createdAt: number;
  mailId: string;
}

export interface GovernanceMailSnapshotResponseDto {
  id: string;
  ownerHandle: string;
  kind: MailKindDto;
  subject: string;
  excerpt: string;
  senderLabel: string;
  unread: boolean;
  important: boolean;
  createdAt: number;
  governanceActorHandle?: string;
  governanceTargetPath?: string;
  governanceTargetLabel?: string;
}

export interface ContributionAdjustmentListResponseDto {
  adjustments: ContributionAdjustmentItemResponseDto[];
}

export interface ContributionAdjustmentCreateResponseDto {
  ok: boolean;
  adjustment: ContributionAdjustmentItemResponseDto;
  mail: GovernanceMailSnapshotResponseDto;
}

export interface GovernanceReviewNotificationListResponseDto {
  notifications: GovernanceReviewNotificationItemResponseDto[];
}

export interface GovernanceReviewNotificationCreateResponseDto {
  ok: boolean;
  notification: GovernanceReviewNotificationItemResponseDto;
  mail: GovernanceMailSnapshotResponseDto;
}
