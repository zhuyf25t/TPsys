import type { MotionObstacleBounds } from "../../local/movement/motionController";
import type { BattleRuntimeAuthoritativeFrame } from "./authoritativeBattleStateBridge";
import type {
  LocalPlayerAuthoritativeCorrectionTarget,
  LocalPlayerAuthoritativeReplayContext
} from "./authoritativeFrameSnapshotApplier";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry
} from "./authoritativeLocalHeroReplay";

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
  applyLocalPlayerAuthoritativeCorrection(target: LocalPlayerAuthoritativeCorrectionTarget): void;
}

export interface PhaserAuthoritativeRenderPipelineFrame {
  frame: BattleRuntimeAuthoritativeFrame;
  receivedAtMs: number;
  localPlayerMovementActive: boolean;
  remoteAuthoritativeHeroIds: Set<string>;
  localPlayerReplay: LocalPlayerAuthoritativeReplayContext;
  applyLocalPlayerAuthoritativeCorrection(target: LocalPlayerAuthoritativeCorrectionTarget): void;
}

/**
 * 中文名：构建 Phaser 权威渲染管线帧（buildPhaserAuthoritativeRenderPipelineFrame）。
 * 游戏职责：把后端权威帧、本地未确认输入、技能预测冷却和 Phaser 碰撞边界组装成一次渲染同步所需的上下文。
 * 架构职责：这里不修改 snapshot、不操作 Phaser actor，只定义 state sync -> local replay -> display correction 的数据边界。
 */
export function buildPhaserAuthoritativeRenderPipelineFrame({
  frame,
  nowMs,
  localPlayerMovementActive,
  obstacleBounds,
  commandHistory,
  lastClientCommandSeq,
  pendingBlinkPrediction,
  pendingDashPrediction,
  blinkCooldownMsOverride,
  dashCooldownMsOverride,
  applyLocalPlayerAuthoritativeCorrection
}: PhaserAuthoritativeRenderPipelineInput): PhaserAuthoritativeRenderPipelineFrame {
  return {
    frame,
    receivedAtMs: nowMs,
    localPlayerMovementActive,
    remoteAuthoritativeHeroIds: new Set(frame.remoteAuthoritativeHeroIds),
    localPlayerReplay: {
      commandHistory,
      lastClientCommandSeq,
      nowMs,
      obstacleBounds,
      pendingBlinkPrediction,
      pendingDashPrediction,
      blinkCooldownMsOverride,
      dashCooldownMsOverride
    },
    applyLocalPlayerAuthoritativeCorrection
  };
}
