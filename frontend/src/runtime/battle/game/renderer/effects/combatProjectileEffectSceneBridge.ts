import type { CombatProjectileEffect } from "../../../microservices/combat/functions/BattleProjectileImpactRules";
import { resolveCombatProjectileEffectKnockbackTarget } from "./functions/CombatProjectileEffectSceneBridgeRules";
import { presentCombatProjectileEffect } from "./combatProjectileEffectPresenter";
import type { CombatProjectileEffectSceneBridgeOptions } from "./objects/CombatProjectileEffectSceneBridgeObjects";

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
          const target = resolveCombatProjectileEffectKnockbackTarget({ heroes: snapshot.heroes, heroId });
          if (target) {
            this.options.applyKnockback(target, direction, strength);
          }
        },
        pushEvent: this.options.pushEvent
      }
    });
  }
}
