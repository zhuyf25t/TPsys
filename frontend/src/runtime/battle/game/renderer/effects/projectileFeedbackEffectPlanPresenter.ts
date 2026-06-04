import type { BattleProjectileFeedbackEffectPlan } from "../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";
import { resolveBattleProjectileFeedbackEffectPresentationAction } from "./functions/ProjectileFeedbackEffectPlanPresentationRules";
import type {
  BattleProjectileFeedbackEffectPresentationAction,
  BattleProjectileFeedbackEffectPresenterCallbacks
} from "./objects/ProjectileFeedbackEffectPlanPresenterObjects";

export function presentBattleProjectileFeedbackEffectPlans(
  effects: readonly BattleProjectileFeedbackEffectPlan[],
  callbacks: BattleProjectileFeedbackEffectPresenterCallbacks
): void {
  effects.forEach((effect) => {
    presentBattleProjectileFeedbackEffectPlan(effect, callbacks);
  });
}

export function presentBattleProjectileFeedbackEffectPlan(
  effect: BattleProjectileFeedbackEffectPlan,
  callbacks: BattleProjectileFeedbackEffectPresenterCallbacks
): void {
  applyBattleProjectileFeedbackEffectPresentationAction(
    resolveBattleProjectileFeedbackEffectPresentationAction({ effect }),
    callbacks
  );
}

function applyBattleProjectileFeedbackEffectPresentationAction(
  action: BattleProjectileFeedbackEffectPresentationAction,
  callbacks: BattleProjectileFeedbackEffectPresenterCallbacks
): void {
  switch (action.kind) {
    case "impactSpark":
      callbacks.createImpactSpark(action.position, action.color);
      return;
    case "pulse":
      callbacks.createPulse(action.position, action.radius, action.color);
      return;
    case "projectileDissipate":
      callbacks.createProjectileDissipate(action.position, action.color);
      return;
    case "projectileTracer":
      callbacks.createProjectileTracer(action.options);
      return;
    case "shockwave":
      callbacks.createShockwave(
        action.position,
        action.startRadius,
        action.endRadius,
        action.color,
        action.durationMs
      );
      return;
  }
}
