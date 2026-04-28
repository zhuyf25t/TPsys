import type Phaser from "phaser";
import type { Hero, Vec2 } from "../../../domain/types";
import { GLOBAL_BACKGROUND_PADDING } from "../../../game/constants";
import { configureBattleCamera, updateBattleCameraTarget } from "./camera/battleCameraDirector";

export function createGameSceneCameraTarget(scene: Phaser.Scene, player: Hero): Phaser.GameObjects.Zone {
  return scene.add.zone(player.position.x, player.position.y, 1, 1);
}

export function configureGameSceneCamera(
  camera: Phaser.Cameras.Scene2D.Camera,
  cameraTarget: Phaser.GameObjects.Zone,
  worldSize: Vec2
): void {
  configureGameSceneCameraBounds(camera, worldSize);
  camera.startFollow(cameraTarget, true, 1, 1);
}

export function configureGameSceneCameraBounds(camera: Phaser.Cameras.Scene2D.Camera, worldSize: Vec2): void {
  configureBattleCamera({
    camera,
    worldSize,
    globalPadding: GLOBAL_BACKGROUND_PADDING
  });
}

export function updateGameSceneCameraTarget({
  pointer,
  scaleSize,
  playerPosition,
  cameraTarget,
  cameraOffset
}: {
  pointer: Phaser.Input.Pointer;
  scaleSize: Phaser.Structs.Size;
  playerPosition: Vec2;
  cameraTarget: Phaser.GameObjects.Zone;
  cameraOffset: Vec2;
}): void {
  updateBattleCameraTarget({
    pointer,
    scaleSize,
    playerPosition,
    cameraTarget,
    cameraOffset
  });
}
