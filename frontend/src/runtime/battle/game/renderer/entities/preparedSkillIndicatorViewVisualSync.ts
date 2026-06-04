import type {
  PreparedSkillIndicatorCircleVisualMutationPlan,
  PreparedSkillIndicatorPlan,
  PreparedSkillIndicatorViewState
} from "./objects/PreparedSkillIndicatorObjects";
import { resolvePreparedSkillIndicatorVisualMutationPlan } from "./functions/PreparedSkillIndicatorRules";

export function syncPreparedSkillIndicatorViewVisuals(
  worldViews: PreparedSkillIndicatorViewState,
  plan: PreparedSkillIndicatorPlan
): void {
  const mutationPlan = resolvePreparedSkillIndicatorVisualMutationPlan(plan);
  syncPreparedSkillIndicatorCircle(worldViews.rangeIndicator, mutationPlan.rangeIndicator);
  syncPreparedSkillIndicatorCircle(worldViews.targetIndicator, mutationPlan.targetIndicator);
}

function syncPreparedSkillIndicatorCircle(
  indicator: PreparedSkillIndicatorViewState["rangeIndicator"],
  plan: PreparedSkillIndicatorCircleVisualMutationPlan
): void {
  indicator.setVisible(plan.visible);
  if (!plan.visible) {
    return;
  }

  indicator.setPosition(plan.position.x, plan.position.y);
  indicator.setRadius(plan.radius);
  indicator.setFillStyle(plan.color, plan.fillAlpha);
  indicator.setStrokeStyle(plan.strokeWidth, plan.color, plan.strokeAlpha);
}
