import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { ProjectileFeedbackState } from "../../../../microservices/combat/functions/BattleProjectileFeedbackRules";
import type {
  BattleProjectileFeedbackEffectPlan,
  BattleRemoteProjectileBirthFeedbackPlan
} from "../../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";
import type { RemoteProjectileBirthDiagnosticsRecordInput } from "../../diagnostics/objects/RemoteViewDiagnosticsObjects";
import type { BattleProjectileFeedbackEffectPresenterCallbacks } from "./ProjectileFeedbackEffectPlanPresenterObjects";

export type RemoteProjectileBirthFeedbackPresenterCallbacks = BattleProjectileFeedbackEffectPresenterCallbacks;

export interface AuthoritativeRemoteProjectileBirthFeedbackPresentation {
  snapshot: GameSnapshot;
  previousProjectileStates: ReadonlyMap<string, ProjectileFeedbackState>;
  callbacks: RemoteProjectileBirthFeedbackPresenterCallbacks;
}

export interface ResolveRemoteProjectileBirthPresentationActionsInput {
  plan: BattleRemoteProjectileBirthFeedbackPlan;
}

export type RemoteProjectileBirthPresentationAction =
  | {
      kind: "recordDiagnostics";
      diagnostics: RemoteProjectileBirthDiagnosticsRecordInput;
    }
  | {
      kind: "presentEffects";
      effects: readonly BattleProjectileFeedbackEffectPlan[];
    };
