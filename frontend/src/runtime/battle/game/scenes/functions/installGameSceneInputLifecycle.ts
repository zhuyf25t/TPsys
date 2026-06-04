import Phaser from "phaser";
import type { GameSceneInputLifecycleHandlers } from "../objects/GameSceneInputLifecycleHandlers";

export function installGameSceneInputLifecycle(
  scene: Phaser.Scene,
  handlers: GameSceneInputLifecycleHandlers
): void {
  scene.input.setDefaultCursor("crosshair");
  scene.input.mouse?.disableContextMenu();
  scene.input.on("pointerdown", handlers.onPointerDown);
  scene.input.on("wheel", handlers.onMouseWheel);
  window.addEventListener("game-wheel-switch", handlers.onGlobalWheelSwitch);

  scene.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
    scene.input.off("pointerdown", handlers.onPointerDown);
    scene.input.off("wheel", handlers.onMouseWheel);
    window.removeEventListener("game-wheel-switch", handlers.onGlobalWheelSwitch);
  });
}
