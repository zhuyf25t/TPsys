import type {
  PhaserAuthoritativeRenderPipelineFrame,
  PhaserAuthoritativeRenderPipelineInput
} from "./objects/BattleAuthoritativeRenderPipelineObjects";

export type {
  PhaserAuthoritativeLocalPlayerCorrectionTarget,
  PhaserAuthoritativeLocalPlayerReplayContext,
  PhaserAuthoritativeRenderPipelineFrame,
  PhaserAuthoritativeRenderPipelineInput
} from "./objects/BattleAuthoritativeRenderPipelineObjects";

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
