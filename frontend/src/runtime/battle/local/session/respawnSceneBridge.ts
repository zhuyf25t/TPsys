import Phaser from "phaser";
import type { GameSnapshot, Vec2 } from "../../../../objects/battle/types";
import { advanceCombatRespawns, type CombatRespawnEffect } from "../combat/combatFrameController";

export interface RespawnSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  getPlayerActor(): Phaser.Physics.Arcade.Image;
  resetWeaponSwitchState(): void;
  stopPlayerMotion(): void;
  pushEvent(type: GameSnapshot["events"][number]["type"], message: string): void;
  createPulse(position: Vec2, radius: number, color: number): void;
}

export class RespawnSceneBridge {
  public constructor(private readonly options: RespawnSceneBridgeOptions) {}

  public updateRespawnTimers(deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    advanceCombatRespawns({
      heroes: snapshot.heroes,
      deltaMs,
      playerHeroId: snapshot.playerHeroId
    }).forEach((effect) => {
      this.handleRespawnEffect(effect);
    });
  }

  private handleRespawnEffect(effect: CombatRespawnEffect): void {
    if (effect.resetSceneTransitionState) {
      this.options.resetWeaponSwitchState();
      this.options.stopPlayerMotion();
    }

    if (effect.isPlayerHero) {
      const playerActor = this.options.getPlayerActor();
      const body = playerActor.body as Phaser.Physics.Arcade.Body;
      body.enable = true;
      playerActor.setPosition(effect.spawn.x, effect.spawn.y);
      playerActor.setVelocity(0, 0);
    }

    this.options.pushEvent(effect.event.type, effect.event.message);
    this.options.createPulse(effect.spawn, 54, 0x7ce5ff);
  }
}
