import type { HeroVisibilityPlan } from "../objects/WorldViewFactoryObjects";
import type { HeroWorldViewVisibilityMutationPlan } from "../objects/HeroWorldViewsSyncObjects";

export function resolveHiddenHeroWorldViewVisibilityMutationPlan(
  visibilityPlan: Extract<HeroVisibilityPlan, { visible: false }>
): HeroWorldViewVisibilityMutationPlan {
  return {
    shadow: false,
    bodyDisc: false,
    silhouetteRing: false,
    hitRing: false,
    statusRing: false,
    weaponStock: false,
    weaponCue: false,
    weaponMuzzle: false,
    weaponOverlay: false,
    sprite: false,
    nameLabel: false,
    healthBackground: false,
    healthFill: false,
    actionBackground: false,
    actionFill: false,
    marker: false,
    localMotionStreaks: {
      kind: "hidden",
      resetPosition: visibilityPlan.resetLocalMotionStreaks
    }
  };
}

export function resolveVisibleHeroWorldViewBaseVisibilityMutationPlan(): HeroWorldViewVisibilityMutationPlan {
  return {
    shadow: true,
    bodyDisc: true,
    silhouetteRing: true,
    hitRing: true,
    statusRing: true,
    sprite: true,
    nameLabel: true,
    healthBackground: true,
    healthFill: true,
    actionBackground: false,
    actionFill: false,
    marker: true,
    localMotionStreaks: {
      kind: "unchanged"
    }
  };
}
