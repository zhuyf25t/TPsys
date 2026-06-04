import { planBattleAuthoritativeRemoteProjectileBirthFeedback } from "../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";
import { recordRemoteProjectileBirthDiagnostics } from "../diagnostics/remoteViewDiagnostics";
import {
  presentBattleProjectileFeedbackEffectPlans
} from "./projectileFeedbackEffectPlanPresenter";
import { resolveRemoteProjectileBirthPresentationActions } from "./functions/RemoteProjectileBirthFeedbackPresentationRules";
import type {
  AuthoritativeRemoteProjectileBirthFeedbackPresentation,
  RemoteProjectileBirthFeedbackPresenterCallbacks,
  RemoteProjectileBirthPresentationAction
} from "./objects/RemoteProjectileBirthFeedbackPresenterObjects";

export function presentAuthoritativeRemoteProjectileBirthFeedback({
  snapshot,
  previousProjectileStates,
  callbacks
}: AuthoritativeRemoteProjectileBirthFeedbackPresentation): void {
  planBattleAuthoritativeRemoteProjectileBirthFeedback({
    snapshot,
    previousProjectileStates
  }).forEach((plan) => {
    resolveRemoteProjectileBirthPresentationActions({ plan }).forEach((action) => {
      applyRemoteProjectileBirthPresentationAction(action, callbacks);
    });
  });
}

function applyRemoteProjectileBirthPresentationAction(
  action: RemoteProjectileBirthPresentationAction,
  callbacks: RemoteProjectileBirthFeedbackPresenterCallbacks
): void {
  switch (action.kind) {
    case "recordDiagnostics":
      recordRemoteProjectileBirthDiagnostics(action.diagnostics);
      return;
    case "presentEffects":
      presentBattleProjectileFeedbackEffectPlans(action.effects, callbacks);
      return;
  }
}
