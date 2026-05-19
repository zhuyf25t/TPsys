import Phaser from "phaser";
import type { Vec2 } from "../../../objects/types";
import { isBattleVisionDiagnosticsEnabled, recordBattleVisionLookAheadDiagnostics } from "../visionDiagnostics";

type ConfigureBattleCameraInput = {
  camera: Phaser.Cameras.Scene2D.Camera;
  worldSize: Vec2;
  globalPadding: number;
};

type UpdateBattleCameraTargetInput = {
  pointer: Phaser.Input.Pointer;
  scaleSize: Phaser.Structs.Size;
  playerPosition: Vec2;
  cameraTarget: Phaser.GameObjects.Zone;
  cameraOffset: Vec2;
};

const CAMERA_DEADZONE = { width: 0, height: 0 };
const POINTER_LOOK_AHEAD_RATIO = 0.38;
const POINTER_LOOK_AHEAD_MAX = { x: 260, y: 260 };
const CAMERA_OFFSET_LERP = { x: 1, y: 1 };

export function configureBattleCamera({ camera, worldSize, globalPadding }: ConfigureBattleCameraInput): void {
  camera.setBounds(
    -globalPadding,
    -globalPadding,
    worldSize.x + globalPadding * 2,
    worldSize.y + globalPadding * 2
  );
  camera.setZoom(1.40);
  camera.roundPixels = false;
  camera.setBackgroundColor("#57a6d9");
  camera.setDeadzone(CAMERA_DEADZONE.width, CAMERA_DEADZONE.height);
}

export function updateBattleCameraTarget({
  pointer,
  scaleSize,
  playerPosition,
  cameraTarget,
  cameraOffset
}: UpdateBattleCameraTargetInput): void {
  const screenCenter = {
    x: scaleSize.width / 2,
    y: scaleSize.height / 2
  };
  const resolvedPointer = resolveCameraPointer(pointer, screenCenter);

  const desiredOffset = {
    x: Phaser.Math.Clamp((resolvedPointer.position.x - screenCenter.x) * POINTER_LOOK_AHEAD_RATIO, -POINTER_LOOK_AHEAD_MAX.x, POINTER_LOOK_AHEAD_MAX.x),
    y: Phaser.Math.Clamp((resolvedPointer.position.y - screenCenter.y) * POINTER_LOOK_AHEAD_RATIO, -POINTER_LOOK_AHEAD_MAX.y, POINTER_LOOK_AHEAD_MAX.y)
  };

  cameraOffset.x = Phaser.Math.Linear(cameraOffset.x, desiredOffset.x, CAMERA_OFFSET_LERP.x);
  cameraOffset.y = Phaser.Math.Linear(cameraOffset.y, desiredOffset.y, CAMERA_OFFSET_LERP.y);
  cameraTarget.setPosition(playerPosition.x + cameraOffset.x, playerPosition.y + cameraOffset.y);
  if (isBattleVisionDiagnosticsEnabled()) {
    recordBattleVisionLookAheadDiagnostics({
      pointer: resolvedPointer.position,
      rawPointer: { x: pointer.x, y: pointer.y },
      pointerReady: resolvedPointer.ready,
      screenCenter,
      desiredOffset,
      actualOffset: cameraOffset,
      targetPosition: { x: cameraTarget.x, y: cameraTarget.y },
      playerPosition,
      ratio: POINTER_LOOK_AHEAD_RATIO,
      max: POINTER_LOOK_AHEAD_MAX,
      lerp: CAMERA_OFFSET_LERP
    });
  }
}

function resolveCameraPointer(
  pointer: Phaser.Input.Pointer,
  screenCenter: Vec2
): { position: Vec2; ready: boolean } {
  const pointerHasEvent = Boolean(pointer.event) || pointer.moveTime > 0 || pointer.downTime > 0 || pointer.upTime > 0;
  if (!pointerHasEvent || !Number.isFinite(pointer.x) || !Number.isFinite(pointer.y)) {
    return {
      position: screenCenter,
      ready: false
    };
  }

  return {
    position: { x: pointer.x, y: pointer.y },
    ready: true
  };
}
