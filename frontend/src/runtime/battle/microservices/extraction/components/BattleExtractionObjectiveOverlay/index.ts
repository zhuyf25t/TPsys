import Phaser from "phaser";
import { renderBattleExtractionObjectiveOverlay } from "./functions/renderBattleExtractionObjectiveOverlay";
import type { BattleExtractionObjectiveOverlaySnapshot } from "./objects/BattleExtractionObjectiveOverlaySnapshot";

export class BattleExtractionObjectiveOverlay {
  private readonly graphics: Phaser.GameObjects.Graphics;

  public constructor(scene: Phaser.Scene) {
    this.graphics = scene.add.graphics().setDepth(3);
    scene.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.graphics.destroy();
    });
  }

  public render(snapshot: BattleExtractionObjectiveOverlaySnapshot): void {
    renderBattleExtractionObjectiveOverlay(this.graphics, snapshot);
  }
}
