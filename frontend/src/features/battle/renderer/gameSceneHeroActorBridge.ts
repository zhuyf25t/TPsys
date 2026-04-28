import type Phaser from "phaser";
import type { Hero } from "../../../domain/types";
import { BASE_MOVE_SPEED, SPRINT_MULTIPLIER } from "../../../game/constants";
import { resolveHeroVisual } from "../../../game/spawn";
import { AuthoritativeFrameSceneBridge } from "./authoritativeFrameSceneBridge";
import type { HeroView } from "./entities/worldViewFactory";
import { LocalHeroDisplay } from "./localHeroDisplayPose";

export interface GameScenePlayerActorHandle {
  playerActor: Phaser.Physics.Arcade.Image;
  localHeroDisplay: LocalHeroDisplay;
  authoritativeFrameBridge: AuthoritativeFrameSceneBridge;
}

export function createGameScenePlayerActor(scene: Phaser.Scene, player: Hero): GameScenePlayerActorHandle {
  const playerActor = scene.physics.add
    .image(player.position.x, player.position.y, resolveHeroVisual(player.heroId).textureKey)
    .setVisible(false);
  playerActor.setRotation(player.facing);
  playerActor.setMaxVelocity(BASE_MOVE_SPEED * SPRINT_MULTIPLIER, BASE_MOVE_SPEED * SPRINT_MULTIPLIER);
  const body = playerActor.body as Phaser.Physics.Arcade.Body;
  body.setSize(player.radius * 2, player.radius * 2, true);

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
  view.sprite.setTintFill(flashColor);
  time.delayedCall(80, () => {
    if (view.sprite.active) {
      view.sprite.setTint(resolveHeroVisual(hero.heroId).tint);
    }
  });
}
