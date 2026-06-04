import {
  planBattleAuthoritativeProjectileTerminalCorrectionTracerEffects,
  planBattleAuthoritativeProjectileTerminalReasonEffects,
  planBattleAuthoritativeProjectileTerminalTracerEffects,
  planBattleProjectileTerminalCorrectionTracerEffects,
  planBattleProjectileTerminalDissipateEffects,
  planBattleProjectileTerminalRocketImpactEffects,
  planBattleProjectileTerminalTracerEffects
} from "../../../microservices/combat/functions/BattleProjectileFeedbackPresentationRules";
import {
  presentBattleProjectileFeedbackEffectPlans
} from "./projectileFeedbackEffectPlanPresenter";
import type {
  AuthoritativeProjectileTerminalTracerPresentation,
  AuthoritativeProjectileTerminalVfxPresentation,
  ProjectileTerminalVfxPresentation
} from "./objects/ProjectileTerminalVfxPresenterObjects";

export function presentProjectileTerminalDissipateVfx({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleProjectileTerminalDissipateEffects({ previous, color }),
    callbacks
  );
}

export function presentProjectileTerminalRocketImpactVfx({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleProjectileTerminalRocketImpactEffects({ previous, color }),
    callbacks
  );
}

export function presentAuthoritativeProjectileTerminalReasonVfx({
  terminal,
  color,
  callbacks
}: AuthoritativeProjectileTerminalVfxPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleAuthoritativeProjectileTerminalReasonEffects({ terminal, color }),
    callbacks
  );
}

export function presentProjectileTerminalTracer({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleProjectileTerminalTracerEffects({ previous, color }),
    callbacks
  );
}

export function presentAuthoritativeProjectileTerminalTracer({
  terminal,
  previous,
  color,
  callbacks
}: AuthoritativeProjectileTerminalTracerPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleAuthoritativeProjectileTerminalTracerEffects({ terminal, previous, color }),
    callbacks
  );
}

export function presentProjectileTerminalCorrectionTracer({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleProjectileTerminalCorrectionTracerEffects({ previous, color }),
    callbacks
  );
}

export function presentAuthoritativeProjectileTerminalCorrectionTracer({
  terminal,
  previous,
  color,
  callbacks
}: AuthoritativeProjectileTerminalTracerPresentation): void {
  presentBattleProjectileFeedbackEffectPlans(
    planBattleAuthoritativeProjectileTerminalCorrectionTracerEffects({ terminal, previous, color }),
    callbacks
  );
}
