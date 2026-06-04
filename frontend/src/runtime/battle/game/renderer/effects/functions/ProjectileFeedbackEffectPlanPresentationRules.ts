import type {
  BattleProjectileFeedbackEffectPresentationAction,
  ResolveBattleProjectileFeedbackEffectPresentationActionInput
} from "../objects/ProjectileFeedbackEffectPlanPresenterObjects";

export function resolveBattleProjectileFeedbackEffectPresentationAction({
  effect
}: ResolveBattleProjectileFeedbackEffectPresentationActionInput): BattleProjectileFeedbackEffectPresentationAction {
  switch (effect.effect) {
    case "impactSpark":
      return {
        kind: "impactSpark",
        position: effect.position,
        color: effect.color
      };
    case "pulse":
      return {
        kind: "pulse",
        position: effect.position,
        radius: effect.radius,
        color: effect.color
      };
    case "projectileDissipate":
      return {
        kind: "projectileDissipate",
        position: effect.position,
        color: effect.color
      };
    case "projectileTracer":
      return {
        kind: "projectileTracer",
        options: effect.options
      };
    case "shockwave":
      return {
        kind: "shockwave",
        position: effect.position,
        startRadius: effect.startRadius,
        endRadius: effect.endRadius,
        color: effect.color,
        durationMs: effect.durationMs
      };
  }
}
