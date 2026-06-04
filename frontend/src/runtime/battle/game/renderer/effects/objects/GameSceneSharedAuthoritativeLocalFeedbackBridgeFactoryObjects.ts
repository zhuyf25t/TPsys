import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ObstacleBounds } from "../../arena/objects/ArenaBuilderObjects";
import type { LocalHeroDisplay } from "../../entities/BattleLocalHeroDisplay";
import type { SceneVfxController } from "../sceneVfxController";

export interface CreateGameSceneSharedAuthoritativeLocalFeedbackBridgeInput {
  getPlayerHero: () => Hero;
  localHeroDisplay: LocalHeroDisplay;
  getWorldSize: () => Vec2;
  getObstacleBounds: () => readonly ObstacleBounds[];
  getNowMs: () => number;
  vfx: SceneVfxController;
}
