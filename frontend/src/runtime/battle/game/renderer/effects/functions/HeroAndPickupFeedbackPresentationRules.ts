import type {
  HeroFeedbackPresentationAction,
  PickupFeedbackPresentationAction,
  ResolveHeroFeedbackPresentationActionsInput,
  ResolvePickupFeedbackPresentationActionsInput
} from "../objects/HeroAndPickupFeedbackPresenterObjects";

export function resolveHeroFeedbackPresentationActions({
  plan
}: ResolveHeroFeedbackPresentationActionsInput): readonly HeroFeedbackPresentationAction[] {
  switch (plan.kind) {
    case "floating-text":
      return [
        {
          kind: "floating-text",
          position: plan.position,
          text: plan.text,
          tone: plan.tone
        }
      ];
    case "pulse":
      return [
        {
          kind: "pulse",
          position: plan.position,
          radius: plan.radius,
          color: plan.color
        }
      ];
    case "flash-hero":
      return [
        {
          kind: "flash-hero",
          heroId: plan.heroId,
          color: plan.color
        }
      ];
    case "impact-spark":
      return [
        {
          kind: "impact-spark",
          position: plan.position,
          color: plan.color
        }
      ];
    case "hit-confirm":
      return [
        {
          kind: "hit-confirm",
          position: plan.position,
          color: plan.color
        }
      ];
    case "camera-shake":
      return [
        {
          kind: "camera-shake",
          durationMs: plan.durationMs,
          intensity: plan.intensity
        }
      ];
  }
}

export function resolvePickupFeedbackPresentationActions({
  plan
}: ResolvePickupFeedbackPresentationActionsInput): readonly PickupFeedbackPresentationAction[] {
  return [
    {
      kind: "floating-text",
      position: plan.floatingText.position,
      text: plan.floatingText.text,
      tone: plan.floatingText.tone
    },
    {
      kind: "pulse",
      position: plan.pulse.position,
      radius: plan.pulse.radius,
      color: plan.pulse.color
    }
  ];
}
