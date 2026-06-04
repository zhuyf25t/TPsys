import type Phaser from "phaser";
import type { AuthoritativeFrameSceneBridge } from "../../authoritative/BattleAuthoritativeFrameSceneBridge";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { LocalHeroDisplay } from "../BattleLocalHeroDisplay";

export interface GameScenePlayerActorHandle {
  playerActor: Phaser.Physics.Arcade.Image;
  localHeroDisplay: LocalHeroDisplay;
  authoritativeFrameBridge: AuthoritativeFrameSceneBridge;
}

export interface ResolveGameSceneHeroActorCreationPlanInput {
  hero: Hero;
}

export interface GameSceneHeroActorCreationPlan {
  position: Vec2;
  textureKey: string;
  visible: boolean;
  rotation: number;
  maxVelocity: Vec2;
  bodySize: Vec2;
  centerBody: boolean;
}

export interface ResolveGameSceneHeroFlashPlanInput {
  hero: Hero;
  flashColor: number;
}

export interface GameSceneHeroFlashPlan {
  fillTint: number;
  restoreDelayMs: number;
  restoreTint: number;
}
