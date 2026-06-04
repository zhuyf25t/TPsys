import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { AuthoritativeFrameSceneBridge } from "../authoritative/BattleAuthoritativeFrameSceneBridge";
import {
  resolveGameSceneHeroActorCreationPlan,
  resolveGameSceneHeroFlashPlan
} from "./functions/BattleGameSceneHeroActorRules";
import { LocalHeroDisplay } from "./BattleLocalHeroDisplay";
import type { GameScenePlayerActorHandle } from "./objects/BattleGameSceneHeroActorObjects";
import type { HeroView } from "./objects/WorldViewFactoryObjects";

export type { GameScenePlayerActorHandle } from "./objects/BattleGameSceneHeroActorObjects";

export function createGameScenePlayerActor(scene: Phaser.Scene, player: Hero): GameScenePlayerActorHandle {
  const plan = resolveGameSceneHeroActorCreationPlan({ hero: player });
  const playerActor = scene.physics.add
    .image(plan.position.x, plan.position.y, plan.textureKey)
    .setVisible(plan.visible);
  playerActor.setRotation(plan.rotation);
  playerActor.setMaxVelocity(plan.maxVelocity.x, plan.maxVelocity.y);
  const body = playerActor.body as Phaser.Physics.Arcade.Body;
  body.setSize(plan.bodySize.x, plan.bodySize.y, plan.centerBody);

  const localHeroDisplay = new LocalHeroDisplay(playerActor);
  return {
    playerActor,
    localHeroDisplay,
    authoritativeFrameBridge: new AuthoritativeFrameSceneBridge(localHeroDisplay)
  };
}

export function flashGameSceneHero(
  time: Phaser.Time.Clock,
  hero: Hero,
  view: HeroView,
  flashColor: number
): void {
  const plan = resolveGameSceneHeroFlashPlan({ hero, flashColor });
  view.sprite.setTintFill(plan.fillTint);
  time.delayedCall(plan.restoreDelayMs, () => {
    if (view.sprite.active) {
      view.sprite.setTint(plan.restoreTint);
    }
  });
}
