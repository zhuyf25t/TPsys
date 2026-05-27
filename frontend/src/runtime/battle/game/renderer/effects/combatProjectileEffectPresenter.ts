import type { GameSnapshot, Vec2 } from "../../../../../objects/battle/types";
import { normalizeVector } from "../../../local/geometry/sceneGeometry";
import { type CombatProjectileEffect } from "../../../local/combat/combatFrameController";

export interface CombatProjectileEffectPresenterCallbacks {
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  flashHero(heroId: string, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
  stopPlayerMotion(): void;
  setPlayerActorDisabled(): void;
  applyKnockback(heroId: string, direction: Vec2, strength: number): void;
  pushEvent(type: GameSnapshot["events"][number]["type"], message: string): void;
}

/** 中文名：presentcombat投射物effect（presentCombatProjectileEffect）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentCombatProjectileEffect(input: {
  effect: CombatProjectileEffect;
  snapshot: GameSnapshot;
  callbacks: CombatProjectileEffectPresenterCallbacks;
}): void {
  const { effect, snapshot, callbacks } = input;

  if (effect.type === "rocket-trail") {
    callbacks.createPulse(effect.position, 10, 0xffb36f);
    callbacks.createImpactSpark(effect.position, 0xffb36f);
    return;
  }

  if (effect.type === "no-damage") {
    return;
  }

  if (effect.type === "rocket-explosion") {
    callbacks.createShockwave(effect.origin, 28, effect.splashRadius, 0xffb677, 250);
    callbacks.createImpactSpark(effect.origin, 0xffd57a);
    callbacks.shakeCamera(110, 0.0022);
    return;
  }

  callbacks.createFloatingText(effect.targetPosition, `-${effect.damage}`, "#ff9a9a");
  callbacks.createImpactSpark(effect.targetPosition, 0xffe2ba);
  callbacks.flashHero(effect.targetHeroId, 0xffffff);

  if (effect.event) {
    callbacks.pushEvent(effect.event.type, effect.event.message);
  }

  if (effect.killed && effect.targetHeroId === snapshot.playerHeroId) {
    callbacks.stopPlayerMotion();
    callbacks.setPlayerActorDisabled();
  }

  if (effect.projectileKind === "rocket-explosion" && effect.origin && effect.targetHeroId !== effect.ownerHeroId) {
    const target = snapshot.heroes.find((hero) => hero.heroId === effect.targetHeroId);
    if (target && target.alive) {
      callbacks.applyKnockback(
        target.heroId,
        normalizeVector({ x: target.position.x - effect.origin.x, y: target.position.y - effect.origin.y }),
        110
      );
    }
  }
}
