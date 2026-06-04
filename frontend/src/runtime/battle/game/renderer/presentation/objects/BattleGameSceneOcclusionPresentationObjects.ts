import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { OccludableView } from "../../arena/objects/ArenaBuilderObjects";
import type { LocalHeroDisplay } from "../../entities/BattleLocalHeroDisplay";

export interface UpdateGameSceneOcclusionInput {
  player: Hero;
  heroes: readonly Hero[];
  sharedAuthoritativeRuntime: boolean;
  localHeroDisplay: LocalHeroDisplay;
  occludables: readonly OccludableView[];
}
