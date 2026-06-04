import Phaser from "phaser";
import { GAME_HEIGHT, GAME_WIDTH } from "../../objects/BattleGameConstants";
import { resolveBattlePhaserGameViewportSize } from "./functions/BattlePhaserGameViewportRules";
import type { CreateBattlePhaserGameInput } from "./objects/BattlePhaserGameObjects";

export function createBattlePhaserGame({ mountNode, scene }: CreateBattlePhaserGameInput): Phaser.Game {
  const viewport = resolveBattlePhaserGameViewportSize({
    mountWidth: mountNode.clientWidth,
    mountHeight: mountNode.clientHeight,
    windowWidth: window.innerWidth,
    windowHeight: window.innerHeight,
    fallbackWidth: GAME_WIDTH,
    fallbackHeight: GAME_HEIGHT
  });

  return new Phaser.Game({
    type: Phaser.AUTO,
    parent: mountNode,
    width: viewport.width,
    height: viewport.height,
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
      width: viewport.width,
      height: viewport.height
    },
    scene: [scene]
  });
}
