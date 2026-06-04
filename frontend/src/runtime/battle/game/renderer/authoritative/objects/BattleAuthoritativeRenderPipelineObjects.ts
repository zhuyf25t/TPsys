import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry
} from "../../../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import type { BattleRuntimeAuthoritativeFrame } from "../../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import type {
  BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget,
  BattleRuntimeAuthoritativeLocalPlayerReplayContext
} from "../../../../microservices/session/functions/BattleRuntimeAuthoritativeHeroSnapshotSync";
import type { MotionObstacleBounds } from "../../../../microservices/world/functions/BattleMotionRules";

export type PhaserAuthoritativeLocalPlayerCorrectionTarget =
  BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget;

export type PhaserAuthoritativeLocalPlayerReplayContext =
  BattleRuntimeAuthoritativeLocalPlayerReplayContext;

export interface PhaserAuthoritativeRenderPipelineInput {
  frame: BattleRuntimeAuthoritativeFrame;
  nowMs: number;
  localPlayerMovementActive: boolean;
  obstacleBounds: readonly MotionObstacleBounds[];
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number;
  pendingBlinkPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  pendingDashPrediction: AuthoritativeLocalHeroPendingDashPrediction | null;
  blinkCooldownMsOverride: number;
  dashCooldownMsOverride: number;
  applyLocalPlayerAuthoritativeCorrection(target: PhaserAuthoritativeLocalPlayerCorrectionTarget): void;
}

export interface PhaserAuthoritativeRenderPipelineFrame {
  frame: BattleRuntimeAuthoritativeFrame;
  receivedAtMs: number;
  localPlayerMovementActive: boolean;
  remoteAuthoritativeHeroIds: Set<string>;
  localPlayerReplay: PhaserAuthoritativeLocalPlayerReplayContext;
  applyLocalPlayerAuthoritativeCorrection(target: PhaserAuthoritativeLocalPlayerCorrectionTarget): void;
}
