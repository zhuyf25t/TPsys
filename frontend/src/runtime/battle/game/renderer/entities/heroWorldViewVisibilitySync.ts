import { setHeroWeaponOverlayVisible } from "./heroWeaponOverlayView";
import { hideLocalHeroMotionStreaks } from "./localHeroMotionStreakView";
import {
  resolveHiddenHeroWorldViewVisibilityMutationPlan,
  resolveVisibleHeroWorldViewBaseVisibilityMutationPlan
} from "./functions/HeroWorldViewVisibilityRules";
import type { HeroWorldViewVisibilityMutationPlan } from "./objects/HeroWorldViewsSyncObjects";
import type {
  HeroView,
  HeroVisibilityPlan
} from "./objects/WorldViewFactoryObjects";

export function hideHeroWorldView(
  view: HeroView,
  visibilityPlan: Extract<HeroVisibilityPlan, { visible: false }>
): void {
  applyHeroWorldViewVisibilityMutationPlan(
    view,
    resolveHiddenHeroWorldViewVisibilityMutationPlan(visibilityPlan)
  );
}

export function showHeroWorldViewBase(view: HeroView): void {
  applyHeroWorldViewVisibilityMutationPlan(
    view,
    resolveVisibleHeroWorldViewBaseVisibilityMutationPlan()
  );
}

function applyHeroWorldViewVisibilityMutationPlan(
  view: HeroView,
  plan: HeroWorldViewVisibilityMutationPlan
): void {
  applyVisible(view.shadow, plan.shadow);
  applyVisible(view.bodyDisc, plan.bodyDisc);
  applyVisible(view.silhouetteRing, plan.silhouetteRing);
  applyVisible(view.hitRing, plan.hitRing);
  applyVisible(view.statusRing, plan.statusRing);
  applyVisible(view.weaponStock, plan.weaponStock);
  applyVisible(view.weaponCue, plan.weaponCue);
  applyVisible(view.weaponMuzzle, plan.weaponMuzzle);
  if (plan.weaponOverlay !== undefined) {
    setHeroWeaponOverlayVisible(view.weaponOverlay, plan.weaponOverlay);
  }
  applyVisible(view.sprite, plan.sprite);
  applyVisible(view.nameLabel, plan.nameLabel);
  applyVisible(view.healthBackground, plan.healthBackground);
  applyVisible(view.healthFill, plan.healthFill);
  applyVisible(view.actionBackground, plan.actionBackground);
  applyVisible(view.actionFill, plan.actionFill);
  if (view.marker) {
    applyVisible(view.marker, plan.marker);
  }
  if (plan.localMotionStreaks.kind === "hidden") {
    hideLocalHeroMotionStreaks(view.localMotionStreaks, plan.localMotionStreaks.resetPosition);
  }
}

function applyVisible(target: { setVisible: (visible: boolean) => unknown }, visible: boolean | undefined): void {
  if (visible !== undefined) {
    target.setVisible(visible);
  }
}
