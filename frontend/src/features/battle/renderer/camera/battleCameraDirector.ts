import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";
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

  const desiredOffset = {
    x: Phaser.Math.Clamp((pointer.x - screenCenter.x) * POINTER_LOOK_AHEAD_RATIO, -POINTER_LOOK_AHEAD_MAX.x, POINTER_LOOK_AHEAD_MAX.x),
    y: Phaser.Math.Clamp((pointer.y - screenCenter.y) * POINTER_LOOK_AHEAD_RATIO, -POINTER_LOOK_AHEAD_MAX.y, POINTER_LOOK_AHEAD_MAX.y)
  };

  cameraOffset.x = Phaser.Math.Linear(cameraOffset.x, desiredOffset.x, CAMERA_OFFSET_LERP.x);
  cameraOffset.y = Phaser.Math.Linear(cameraOffset.y, desiredOffset.y, CAMERA_OFFSET_LERP.y);
  cameraTarget.setPosition(playerPosition.x + cameraOffset.x, playerPosition.y + cameraOffset.y);
  if (isBattleVisionDiagnosticsEnabled()) {
    recordBattleVisionLookAheadDiagnostics({
      pointer: { x: pointer.x, y: pointer.y },
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
