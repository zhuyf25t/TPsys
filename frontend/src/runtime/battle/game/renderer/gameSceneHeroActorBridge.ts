import type Phaser from "phaser";
import type { Hero } from "../../../../objects/battle/types";
import { BASE_MOVE_SPEED, SPRINT_MULTIPLIER } from "../constants";
import { resolveHeroVisual } from "../spawn";
import { AuthoritativeFrameSceneBridge } from "./authoritativeFrameSceneBridge";
import type { HeroView } from "./entities/worldViewFactory";
import { LocalHeroDisplay } from "./localHeroDisplayPose";

export interface GameScenePlayerActorHandle {
  playerActor: Phaser.Physics.Arcade.Image;
  localHeroDisplay: LocalHeroDisplay;
  authoritativeFrameBridge: AuthoritativeFrameSceneBridge;
}

/** 中文名：创建gamescene玩家actor（createGameScenePlayerActor）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：flashgamescene英雄（flashGameSceneHero）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
