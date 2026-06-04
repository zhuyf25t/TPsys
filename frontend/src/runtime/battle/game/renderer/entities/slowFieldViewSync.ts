import {
  createSlowFieldView,
  releaseSlowFieldView,
  syncSlowFieldViewVisuals
} from "./slowFieldViewLifecycle";
import type {
  SlowFieldViewSyncContext
} from "./objects/SlowFieldViewObjects";

export function syncSlowFieldViews({ scene, snapshot, worldViews }: SlowFieldViewSyncContext): void {
  const liveIds = worldViews.scratchLiveSlowFieldIds;
  liveIds.clear();

  snapshot.slowFields.forEach((field) => {
    liveIds.add(field.fieldId);
    const existing = worldViews.slowFieldViews.get(field.fieldId) ?? createSlowFieldView(scene, field);
    worldViews.slowFieldViews.set(field.fieldId, existing);
    syncSlowFieldViewVisuals(existing, field);
  });

  for (const [fieldId, view] of worldViews.slowFieldViews.entries()) {
    if (liveIds.has(fieldId)) {
      continue;
    }

    releaseSlowFieldView(view);
    worldViews.slowFieldViews.delete(fieldId);
  }
}
