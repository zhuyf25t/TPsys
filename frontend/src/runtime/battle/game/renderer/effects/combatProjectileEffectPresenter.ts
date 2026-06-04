import { resolveCombatProjectileEffectPresentationPlan } from "./functions/CombatProjectileEffectPresentationRules";
import type {
  CombatProjectileEffectPresentationAction,
  CombatProjectileEffectPresenterCallbacks,
  PresentCombatProjectileEffectInput
} from "./objects/CombatProjectileEffectPresenterObjects";

/** 中文名：presentcombat投射物effect（presentCombatProjectileEffect）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentCombatProjectileEffect(input: PresentCombatProjectileEffectInput): void {
  const { effect, snapshot, callbacks } = input;
  const presentationPlan = resolveCombatProjectileEffectPresentationPlan({ effect, snapshot });

  presentationPlan.actions.forEach((action) => {
    presentCombatProjectileEffectAction(action, callbacks);
  });
}

function presentCombatProjectileEffectAction(
  action: CombatProjectileEffectPresentationAction,
  callbacks: CombatProjectileEffectPresenterCallbacks
): void {
  switch (action.kind) {
    case "pulse":
      callbacks.createPulse(action.position, action.radius, action.color);
      return;
    case "impactSpark":
      callbacks.createImpactSpark(action.position, action.color);
      return;
    case "shockwave":
      callbacks.createShockwave(action.position, action.startRadius, action.endRadius, action.color, action.durationMs);
      return;
    case "floatingText":
      callbacks.createFloatingText(action.position, action.text, action.color);
      return;
    case "flashHero":
      callbacks.flashHero(action.heroId, action.color);
      return;
    case "pushEvent":
      callbacks.pushEvent(action.eventType, action.message);
      return;
    case "shakeCamera":
      callbacks.shakeCamera(action.durationMs, action.intensity);
      return;
    case "stopPlayerMotion":
      callbacks.stopPlayerMotion();
      return;
    case "setPlayerActorDisabled":
      callbacks.setPlayerActorDisabled();
      return;
    case "knockback":
      callbacks.applyKnockback(action.heroId, action.direction, action.strength);
      return;
  }
}
