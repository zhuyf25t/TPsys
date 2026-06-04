import type {
  RemoteProjectileBirthPresentationAction,
  ResolveRemoteProjectileBirthPresentationActionsInput
} from "../objects/RemoteProjectileBirthFeedbackPresenterObjects";

export function resolveRemoteProjectileBirthPresentationActions({
  plan
}: ResolveRemoteProjectileBirthPresentationActionsInput): readonly RemoteProjectileBirthPresentationAction[] {
  return [
    {
      kind: "recordDiagnostics",
      diagnostics: {
        projectile: plan.projectile,
        ownerDisplayName: plan.ownerDisplayName,
        position: plan.position
      }
    },
    {
      kind: "presentEffects",
      effects: plan.effects
    }
  ];
}
