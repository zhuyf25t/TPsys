import type { GameSnapshot, Hero, Vec2 } from "../../../../domain/types";
import type { CombatProjectileEffect } from "../../runtime-local/combat/combatFrameController";
import { presentCombatProjectileEffect } from "./combatProjectileEffectPresenter";

export interface CombatProjectileEffectSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  flashHero(heroId: string, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
  stopPlayerMotion(): void;
  setPlayerActorDisabled(): void;
  applyKnockback(hero: Hero, direction: Vec2, strength: number): void;
  pushEvent(type: GameSnapshot["events"][number]["type"], message: string): void;
}

export class CombatProjectileEffectSceneBridge {
  public constructor(private readonly options: CombatProjectileEffectSceneBridgeOptions) {}

  public present(effect: CombatProjectileEffect): void {
    const snapshot = this.options.getSnapshot();

    presentCombatProjectileEffect({
      effect,
      snapshot,
      callbacks: {
        createPulse: this.options.createPulse,
        createImpactSpark: this.options.createImpactSpark,
        createShockwave: this.options.createShockwave,
        createFloatingText: this.options.createFloatingText,
        flashHero: this.options.flashHero,
        shakeCamera: this.options.shakeCamera,
        stopPlayerMotion: this.options.stopPlayerMotion,
        setPlayerActorDisabled: this.options.setPlayerActorDisabled,
        applyKnockback: (heroId, direction, strength) => {
          const target = snapshot.heroes.find((hero) => hero.heroId === heroId);
          if (target && target.alive) {
            this.options.applyKnockback(target, direction, strength);
          }
        },
        pushEvent: this.options.pushEvent
      }
    });
  }
}
