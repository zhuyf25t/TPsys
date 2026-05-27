import type Phaser from "phaser";
import type { Hero, Vec2 } from "../../../../objects/battle/types";
import { GLOBAL_BACKGROUND_PADDING } from "../constants";
import { configureBattleCamera, updateBattleCameraTarget } from "./camera/battleCameraDirector";

/** 中文名：创建gamescenecamera目标（createGameSceneCameraTarget）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createGameSceneCameraTarget(scene: Phaser.Scene, player: Hero): Phaser.GameObjects.Zone {
  return scene.add.zone(player.position.x, player.position.y, 1, 1);
}

/** 中文名：configuregamescenecamera（configureGameSceneCamera）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function configureGameSceneCamera(
  camera: Phaser.Cameras.Scene2D.Camera,
  cameraTarget: Phaser.GameObjects.Zone,
  worldSize: Vec2
): void {
  configureGameSceneCameraBounds(camera, worldSize);
  camera.startFollow(cameraTarget, true, 1, 1);
}

/** 中文名：configuregamescenecamerabounds（configureGameSceneCameraBounds）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function configureGameSceneCameraBounds(camera: Phaser.Cameras.Scene2D.Camera, worldSize: Vec2): void {
  configureBattleCamera({
    camera,
    worldSize,
    globalPadding: GLOBAL_BACKGROUND_PADDING
  });
}

/** 中文名：更新gamescenecamera目标（updateGameSceneCameraTarget）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
