import { isBattleVisionDiagnosticsEnabled, recordBattleVisionLookAheadDiagnostics } from "../diagnostics/visionDiagnostics";
import { resolveBattleCameraConfigurationPlan, resolveBattleCameraTargetUpdatePlan } from "./functions/BattleCameraRules";
import type { ConfigureBattleCameraInput, UpdateBattleCameraTargetInput } from "./objects/BattleCameraObjects";

export function configureBattleCamera({ camera, worldSize, globalPadding }: ConfigureBattleCameraInput): void {
  const plan = resolveBattleCameraConfigurationPlan(worldSize, globalPadding);
  camera.setBounds(plan.bounds.x, plan.bounds.y, plan.bounds.width, plan.bounds.height);
  camera.setZoom(plan.zoom);
  camera.roundPixels = plan.roundPixels;
  camera.setBackgroundColor(plan.backgroundColor);
  camera.setDeadzone(plan.deadzone.width, plan.deadzone.height);
}

export function updateBattleCameraTarget({
  pointer,
  scaleSize,
  playerPosition,
  cameraTarget,
  cameraOffset
}: UpdateBattleCameraTargetInput): void {
  const plan = resolveBattleCameraTargetUpdatePlan({
    pointer: {
      position: { x: pointer.x, y: pointer.y },
      hasEvent: Boolean(pointer.event),
      moveTime: pointer.moveTime,
      downTime: pointer.downTime,
      upTime: pointer.upTime
    },
    scaleSize,
    playerPosition,
    cameraOffset
  });

  cameraOffset.x = plan.nextOffset.x;
  cameraOffset.y = plan.nextOffset.y;
  cameraTarget.setPosition(plan.targetPosition.x, plan.targetPosition.y);
  if (isBattleVisionDiagnosticsEnabled()) {
    recordBattleVisionLookAheadDiagnostics({
      pointer: plan.resolvedPointer.position,
      rawPointer: { x: pointer.x, y: pointer.y },
      pointerReady: plan.resolvedPointer.ready,
      screenCenter: plan.screenCenter,
      desiredOffset: plan.desiredOffset,
      actualOffset: plan.nextOffset,
      targetPosition: plan.targetPosition,
      playerPosition,
      ratio: plan.ratio,
      max: plan.max,
      lerp: plan.lerp
    });
  }
}