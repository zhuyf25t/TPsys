import type Phaser from "phaser";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { WorldViewState } from "../../entities/worldViewFactory";
import type { SceneVfxController } from "../sceneVfxController";

export interface CreateGameSceneBattleFeedbackBridgeInput {
  getSnapshot: () => GameSnapshot;
  getWorldViews: () => WorldViewState;
  flashHero: (heroId: string, color: number) => void;
  vfx: SceneVfxController;
  camera: Phaser.Cameras.Scene2D.Camera;
}
