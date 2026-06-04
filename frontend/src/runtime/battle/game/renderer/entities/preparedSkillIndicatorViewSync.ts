import { resolvePreparedSkillIndicatorPlan } from "./functions/PreparedSkillIndicatorRules";
import { syncPreparedSkillIndicatorViewVisuals } from "./preparedSkillIndicatorViewVisualSync";
import type {
  PreparedSkillIndicatorViewSyncContext
} from "./objects/PreparedSkillIndicatorObjects";

export type {
  PreparedSkillIndicatorDisplayOverride,
  PreparedSkillIndicatorViewState,
  PreparedSkillIndicatorViewSyncContext
} from "./objects/PreparedSkillIndicatorObjects";

export function syncPreparedSkillIndicatorViews({
  snapshot,
  worldViews,
  pointerWorld,
  isBlinkTargetValid,
  isPreparedTargetValid,
  sharedAuthoritativeRuntime = false,
  localHeroDisplayOverride
}: PreparedSkillIndicatorViewSyncContext): void {
  const plan = resolvePreparedSkillIndicatorPlan({
    snapshot,
    pointerWorld,
    isBlinkTargetValid,
    isPreparedTargetValid,
    sharedAuthoritativeRuntime,
    localHeroDisplayOverride
  });

  syncPreparedSkillIndicatorViewVisuals(worldViews, plan);
}
