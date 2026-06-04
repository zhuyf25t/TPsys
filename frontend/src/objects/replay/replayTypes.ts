import type {
  BattleReplayFrameState,
  BattleReplayHeroFrameState,
  BattleReplayPickupFrameState,
  BattleReplayProjectileFrameState
} from "../battle/microservices/projections/objects/replay/BattleReplayFrameState";

export type ReplayHeroFrame = BattleReplayHeroFrameState;
export type ReplayProjectileFrame = BattleReplayProjectileFrameState;
export type ReplayPickupFrame = BattleReplayPickupFrameState;
export type ReplayFrame = BattleReplayFrameState;

export interface ReplayPlayback {
  id: string;
  playbackAvailable?: boolean;
  title: string;
  modeLabel: string;
  resultLabel: string;
  finishedAtLabel: string;
  mapLabel: string;
  highlightLine: string;
  timelineHint: string;
  playersLine: string;
  score: number;
  placement: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  frames: ReplayFrame[];
}

export interface ReplayExportArtifact {
  filename: string;
  json: string;
}

export interface ReplayCatalogItemResponseDto {
  replayId: string;
  battleId: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
  finishedAt: number;
  finishedAtLabel: string;
  mapLabel: string;
  highlightLine: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  score: number;
  placement: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  frameCount: number;
  playbackAvailable: boolean;
}

export interface ReplayCatalogResponseDto {
  replays: ReplayCatalogItemResponseDto[];
}

export interface ReplayDetailRecordResponseDto extends ReplayCatalogItemResponseDto {
  handle: string;
  displayName: string;
  currentLoadout: string | null;
  frames: ReplayFrame[];
}

export interface ReplayDetailResponseDto {
  replay: ReplayDetailRecordResponseDto;
}

export interface ReplayCommentResponseDto {
  id: string;
  replayId: string;
  authorHandle: string;
  body: string;
  createdAt: number;
}

export interface ReplayCommentsResponseDto {
  comments: ReplayCommentResponseDto[];
}

export interface ReplayCommentWrapperResponseDto {
  comment: ReplayCommentResponseDto;
}

export interface ReplayRecordApiRequestDto {
  replayId?: string;
  battleId?: string;
  handle?: string;
  displayName?: string;
  finishedAt?: number;
  finishedAtLabel?: string;
  title?: string;
  modeLabel?: string;
  resultLabel?: string;
  mapLabel?: string;
  highlightLine?: string;
  coverLabel?: string;
  playersLine?: string;
  timelineHint?: string;
  score?: number;
  placement?: number | null;
  durationMs?: number;
  aliveAtEnd?: boolean;
  thumbnailDataUrl?: string | null;
  currentLoadout?: string | null;
  frameCount?: number;
  playbackAvailable?: boolean;
  framesJson?: string;
  frames?: ReplayFrame[];
}

export interface ReplayCommentApiRequestDto {
  authorHandle?: string;
  body?: string;
}

export interface ReplayCatalogApiRequestDto {
  limit?: number;
  handle?: string;
}
