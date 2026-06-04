import {
  resolveBattleHeroFeedbackPlans
} from "../../../microservices/actors/functions/BattleHeroFeedbackRules";
import {
  resolveBattleItemPickupFeedbackPlan,
  resolveBattleWeaponPickupFeedbackPlan
} from "../../../microservices/abilities/functions/BattlePickupFeedbackRules";
import {
  resolveHeroFeedbackPresentationActions,
  resolvePickupFeedbackPresentationActions
} from "./functions/HeroAndPickupFeedbackPresentationRules";
import type {
  AuthoritativePickupFeedbackPresentationOptions,
  HeroFeedbackPresentationAction,
  HeroFeedbackPresentationCallbacks,
  HeroFeedbackPresentationOptions,
  PickupFeedbackPresentationAction,
  PickupFeedbackPresentationCallbacks
} from "./objects/HeroAndPickupFeedbackPresenterObjects";

export function presentHeroFeedback({
  snapshot,
  previousHeroStates,
  sharedAuthoritativeRuntime,
  getHeroDisplayPosition,
  flashHero,
  showFloatingText,
  createPulse,
  createImpactSpark,
  createHitConfirm,
  shakeCamera
}: HeroFeedbackPresentationOptions): void {
  snapshot.heroes.forEach((hero) => {
    const previous = previousHeroStates.get(hero.heroId);
    if (!previous) {
      return;
    }

    resolveBattleHeroFeedbackPlans({
      hero,
      previous,
      playerHeroId: snapshot.playerHeroId,
      sharedAuthoritativeRuntime,
      displayPosition: getHeroDisplayPosition(hero.heroId)
    }).forEach((plan) => {
      resolveHeroFeedbackPresentationActions({ plan }).forEach((action) =>
        applyHeroFeedbackPresentationAction(action, {
          flashHero,
          showFloatingText,
          createPulse,
          createImpactSpark,
          createHitConfirm,
          shakeCamera
        })
      );
    });
  });
}

export function presentAuthoritativePickupFeedback({
  snapshot,
  previousWeaponPickupStates,
  previousItemPickupStates,
  showFloatingText,
  createPulse
}: AuthoritativePickupFeedbackPresentationOptions): void {
  snapshot.weaponPickups.forEach((pickup) => {
    const plan = resolveBattleWeaponPickupFeedbackPlan({
      pickup,
      previous: previousWeaponPickupStates.get(pickup.pickupId)
    });
    if (plan) {
      resolvePickupFeedbackPresentationActions({ plan }).forEach((action) =>
        applyPickupFeedbackPresentationAction(action, { showFloatingText, createPulse })
      );
    }
  });

  snapshot.itemPickups.forEach((pickup) => {
    const plan = resolveBattleItemPickupFeedbackPlan({
      pickup,
      previous: previousItemPickupStates.get(pickup.pickupId)
    });
    if (plan) {
      resolvePickupFeedbackPresentationActions({ plan }).forEach((action) =>
        applyPickupFeedbackPresentationAction(action, { showFloatingText, createPulse })
      );
    }
  });
}

function applyHeroFeedbackPresentationAction(
  action: HeroFeedbackPresentationAction,
  callbacks: HeroFeedbackPresentationCallbacks
): void {
  switch (action.kind) {
    case "floating-text":
      callbacks.showFloatingText(action.position, action.text, action.tone);
      return;
    case "pulse":
      callbacks.createPulse(action.position, action.radius, action.color);
      return;
    case "flash-hero":
      callbacks.flashHero(action.heroId, action.color);
      return;
    case "impact-spark":
      callbacks.createImpactSpark(action.position, action.color);
      return;
    case "hit-confirm":
      callbacks.createHitConfirm(action.position, action.color);
      return;
    case "camera-shake":
      callbacks.shakeCamera(action.durationMs, action.intensity);
      return;
  }
}

function applyPickupFeedbackPresentationAction(
  action: PickupFeedbackPresentationAction,
  callbacks: PickupFeedbackPresentationCallbacks
): void {
  switch (action.kind) {
    case "floating-text":
      callbacks.showFloatingText(action.position, action.text, action.tone);
      return;
    case "pulse":
      callbacks.createPulse(action.position, action.radius, action.color);
      return;
  }
}
