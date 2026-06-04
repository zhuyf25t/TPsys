import type {
  BattleAuthoritativeProjectileTerminalReasonFeedbackPlanInput,
  BattleAuthoritativeProjectileTerminalTracerFeedbackPlanInput,
  BattleProjectileTerminalFeedbackPlanInput
} from "../../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";
import type { BattleProjectileFeedbackEffectPresenterCallbacks } from "./ProjectileFeedbackEffectPlanPresenterObjects";

export type ProjectileTerminalVfxPresenterCallbacks = BattleProjectileFeedbackEffectPresenterCallbacks;

export interface ProjectileTerminalVfxPresentation extends BattleProjectileTerminalFeedbackPlanInput {
  callbacks: ProjectileTerminalVfxPresenterCallbacks;
}

export interface AuthoritativeProjectileTerminalVfxPresentation
  extends BattleAuthoritativeProjectileTerminalReasonFeedbackPlanInput {
  callbacks: ProjectileTerminalVfxPresenterCallbacks;
}

export interface AuthoritativeProjectileTerminalTracerPresentation
  extends BattleAuthoritativeProjectileTerminalTracerFeedbackPlanInput {
  callbacks: ProjectileTerminalVfxPresenterCallbacks;
}
