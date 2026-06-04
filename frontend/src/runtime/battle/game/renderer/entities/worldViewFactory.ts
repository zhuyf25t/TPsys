import { syncHeroWorldViews } from "./heroWorldViewsSync";
import { syncPickupViews } from "./pickupViewSync";
import { syncProjectileViews } from "./projectileViewSync";
import { syncSlowFieldViews } from "./slowFieldViewSync";
import type {
  WorldViewFactoryContext,
  WorldViewState,
  WorldViewSyncContext
} from "./objects/WorldViewFactoryObjects";
import { syncWorldViewIndicators } from "./worldViewIndicatorSync";
import { createInitialWorldViewState } from "./worldViewStateFactory";

export {
  getHeroDisplayPositionFromWorldViews as getHeroDisplayPosition,
  getProjectileDisplayPositionFromWorldViews as getProjectileDisplayPosition
} from "./worldViewDisplayPositionReader";
export { syncWorldViewIndicators as syncIndicators } from "./worldViewIndicatorSync";

export type {
  HeroActionProgressPlan,
  HeroView,
  LocalHeroDisplayOverride,
  WorldViewFactoryContext,
  WorldViewState,
  WorldViewSyncContext
} from "./objects/WorldViewFactoryObjects";

export { syncPickupViews };

export function createWorldViewState(context: WorldViewFactoryContext): WorldViewState {
  return createInitialWorldViewState(context);
}

export function syncHeroViews(context: WorldViewSyncContext): void {
  syncHeroWorldViews(context);
}

export function syncWorldViews(context: WorldViewSyncContext): void {
  syncHeroViews(context);
  syncSlowFieldViews(context);
  syncProjectileViews(context);
  syncPickupViews(context);
  syncWorldViewIndicators(context);
}
