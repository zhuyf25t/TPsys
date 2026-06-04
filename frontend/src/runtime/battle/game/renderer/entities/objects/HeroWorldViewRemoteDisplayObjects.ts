import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type {
  HeroDisplayState,
  HeroDisplayStatePlan,
  HeroView,
  WorldViewState
} from "./WorldViewFactoryObjects";

export interface ResolveHeroWorldViewDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: WorldViewState;
  view: HeroView;
  hero: Hero;
  deltaMs: number;
  displayStatePlan: HeroDisplayStatePlan;
}

export interface RecordHeroWorldViewRemoteDiagnosticsInput {
  hero: Hero;
  displayState: HeroDisplayState;
  displayStatePlan: HeroDisplayStatePlan;
}
