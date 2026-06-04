import type Phaser from "phaser";

export interface GameSceneInputLifecycleHandlers {
  readonly onPointerDown: (pointer: Phaser.Input.Pointer) => void;
  readonly onMouseWheel: (
    pointer: Phaser.Input.Pointer,
    gameObjects: Phaser.GameObjects.GameObject[],
    deltaX: number,
    deltaY: number,
    deltaZ: number,
    event: WheelEvent
  ) => void;
  readonly onGlobalWheelSwitch: (event: Event) => void;
}
