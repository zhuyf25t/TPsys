import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

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

export interface ResolveCombatProjectileEffectKnockbackTargetInput {
  heroes: readonly Hero[];
  heroId: string;
}
