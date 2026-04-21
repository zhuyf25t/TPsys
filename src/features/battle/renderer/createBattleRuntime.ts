import Phaser from "phaser";
import { GAME_HEIGHT, GAME_WIDTH } from "../../../game/constants";
import type { GameSnapshot } from "../../../domain/types";
import { installWheelSwitchBridge, type WheelSwitchBridge } from "../input/wheelSwitchAdapter";
import { GameScene } from "../../../scenes/GameScene";

export interface BattleRuntimeHandle {
  destroy: () => void;
  readSnapshot: () => GameSnapshot | null;
  captureThumbnail: () => string | null;
}

export interface CreateBattleRuntimeOptions {
  mountNode: HTMLElement;
  hudRoot: HTMLElement;
  initialSnapshot?: GameSnapshot | null;
}

function installContextMenuLock(): () => void {
  const listener = (event: MouseEvent): void => {
    event.preventDefault();
  };

  window.addEventListener("contextmenu", listener);

  return () => {
    window.removeEventListener("contextmenu", listener);
  };
}

export function createBattleRuntime({
  mountNode,
  hudRoot,
  initialSnapshot = null
}: CreateBattleRuntimeOptions): BattleRuntimeHandle {
  mountNode.replaceChildren();
  hudRoot.replaceChildren();
  hudRoot.id = "hud-root";

  const cleanupWheelBridge: WheelSwitchBridge = installWheelSwitchBridge();
  const cleanupContextMenuLock = installContextMenuLock();

  const scene = new GameScene(initialSnapshot);
  let destroyed = false;
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: mountNode,
    width: mountNode.clientWidth || window.innerWidth || GAME_WIDTH,
    height: mountNode.clientHeight || window.innerHeight || GAME_HEIGHT,
    pixelArt: true,
    backgroundColor: "#0b1016",
    physics: {
      default: "arcade",
      arcade: {
        debug: false
      }
    },
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
      width: mountNode.clientWidth || window.innerWidth || GAME_WIDTH,
      height: mountNode.clientHeight || window.innerHeight || GAME_HEIGHT
    },
    scene: [scene]
  });

  function readSnapshot(): GameSnapshot | null {
    return scene.exportSnapshot();
  }

  function captureThumbnail(): string | null {
    const canvas = mountNode.querySelector("canvas");
    if (!(canvas instanceof HTMLCanvasElement)) {
      return null;
    }

    try {
      return canvas.toDataURL("image/png");
    } catch {
      return null;
    }
  }

  return {
    readSnapshot,
    captureThumbnail,
    destroy: () => {
      if (destroyed) {
        return;
      }

      destroyed = true;
      cleanupWheelBridge();
      cleanupContextMenuLock();
      game.destroy(true);
      mountNode.replaceChildren();
      hudRoot.replaceChildren();
    }
  };
}
