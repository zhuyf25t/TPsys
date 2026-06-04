import type Phaser from "phaser";
import { createHeroWorldView } from "./heroWorldViewFactory";
import {
  createItemPickupView,
  createWeaponPickupView
} from "./pickupViewPresentation";
import { resolveWorldViewIndicatorCreationPlan } from "./functions/WorldViewStateFactoryRules";
import type {
  WorldViewFactoryContext,
  WorldViewState
} from "./objects/WorldViewFactoryObjects";
import type {
  WorldViewIndicatorCreationPlan,
  WorldViewIndicatorStyle,
  WorldViewIndicatorViews
} from "./objects/WorldViewStateFactoryObjects";

export function createInitialWorldViewState(context: WorldViewFactoryContext): WorldViewState {
  const { scene, snapshot, getBaseHeroScale } = context;
  const heroViews: WorldViewState["heroViews"] = new Map();
  const remoteHeroInterpolationBuffers: WorldViewState["remoteHeroInterpolationBuffers"] = new Map();
  const projectileInterpolationBuffers: WorldViewState["projectileInterpolationBuffers"] = new Map();
  const projectileViews: WorldViewState["projectileViews"] = new Map();
  const projectileViewPool: WorldViewState["projectileViewPool"] = [];
  const slowFieldViews: WorldViewState["slowFieldViews"] = new Map();
  const pickupViews: WorldViewState["pickupViews"] = new Map();
  const itemPickupViews: WorldViewState["itemPickupViews"] = new Map();
  const scratchActiveRemoteHeroIds = new Set<string>();
  const scratchLiveProjectileIds = new Set<string>();
  const scratchLiveSlowFieldIds = new Set<string>();
  const scratchLiveWeaponPickupIds = new Set<string>();
  const scratchLiveItemPickupIds = new Set<string>();

  snapshot.heroes.forEach((hero) => {
    heroViews.set(hero.heroId, createHeroWorldView({
      scene,
      hero,
      playerHeroId: snapshot.playerHeroId,
      getBaseHeroScale
    }));
  });

  snapshot.weaponPickups.forEach((pickup) => {
    pickupViews.set(pickup.pickupId, createWeaponPickupView(scene, pickup));
  });

  snapshot.itemPickups.forEach((pickup) => {
    itemPickupViews.set(pickup.pickupId, createItemPickupView(scene, pickup));
  });

  const indicators = createWorldViewIndicators(scene, resolveWorldViewIndicatorCreationPlan());

  return {
    heroViews,
    remoteHeroInterpolationBuffers,
    projectileInterpolationBuffers,
    projectileViews,
    projectileViewPool,
    slowFieldViews,
    pickupViews,
    itemPickupViews,
    scratchActiveRemoteHeroIds,
    scratchLiveProjectileIds,
    scratchLiveSlowFieldIds,
    scratchLiveWeaponPickupIds,
    scratchLiveItemPickupIds,
    ...indicators
  };
}

function createWorldViewIndicators(
  scene: Phaser.Scene,
  plan: WorldViewIndicatorCreationPlan
): WorldViewIndicatorViews {
  return {
    rangeIndicator: createWorldViewIndicator(scene, plan.rangeIndicator),
    targetIndicator: createWorldViewIndicator(scene, plan.targetIndicator)
  };
}

function createWorldViewIndicator(
  scene: Phaser.Scene,
  style: WorldViewIndicatorStyle
): Phaser.GameObjects.Arc {
  const indicator = scene.add
    .circle(style.position.x, style.position.y, style.radius, style.fillColor, style.fillAlpha)
    .setDepth(style.depth)
    .setVisible(style.visible);
  indicator.setStrokeStyle(style.strokeWidth, style.strokeColor, style.strokeAlpha);
  return indicator;
}
