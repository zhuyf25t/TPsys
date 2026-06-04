import type { WorldViewSyncContext } from "./objects/WorldViewFactoryObjects";
import { syncPreparedSkillIndicatorViews } from "./preparedSkillIndicatorViewSync";

export function syncWorldViewIndicators({
  snapshot,
  worldViews,
  pointerWorld,
  isBlinkTargetValid,
  isPreparedTargetValid,
  sharedAuthoritativeRuntime = false,
  localHeroDisplayOverride
}: WorldViewSyncContext): void {
  syncPreparedSkillIndicatorViews({
    snapshot,
    worldViews,
    pointerWorld,
    isBlinkTargetValid,
    isPreparedTargetValid,
    sharedAuthoritativeRuntime,
    localHeroDisplayOverride
  });
}
