import Phaser from "phaser";
import type { Vec2 } from "../../../../domain/types";

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

export function configureBattleCamera({ camera, worldSize, globalPadding }: ConfigureBattleCameraInput): void {
  camera.setBounds(
    -globalPadding,
    -globalPadding,
    worldSize.x + globalPadding * 2,
    worldSize.y + globalPadding * 2
  );
  camera.setZoom(1.32);
  camera.roundPixels = true;
  camera.setBackgroundColor("#57a6d9");
  camera.setDeadzone(140, 100);
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
    x: Phaser.Math.Clamp((pointer.x - screenCenter.x) * 0.38, -260, 260),
    y: Phaser.Math.Clamp((pointer.y - screenCenter.y) * 0.38, -260, 260)
  };

  cameraOffset.x = Phaser.Math.Linear(cameraOffset.x, desiredOffset.x, 0.12);
  cameraOffset.y = Phaser.Math.Linear(cameraOffset.y, desiredOffset.y, 0.12);
  cameraTarget.setPosition(playerPosition.x + cameraOffset.x, playerPosition.y + cameraOffset.y);
}
