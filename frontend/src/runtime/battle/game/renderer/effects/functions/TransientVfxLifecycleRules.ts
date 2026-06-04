import type {
  ResolveSceneVfxDiagnosticsSnapshotInput,
  ResolveTransientVfxPeakActiveCountInput,
  SceneVfxDiagnosticsSnapshot
} from "../objects/TransientVfxLifecycleObjects";

const TRANSIENT_VFX_ACTIVE_LIMIT = 120;
const TRANSIENT_VFX_SLOT_COMPACTION_LIMIT = TRANSIENT_VFX_ACTIVE_LIMIT * 2;

export function shouldReleaseOldestTransientVfx(activeTransientCount: number): boolean {
  return activeTransientCount > TRANSIENT_VFX_ACTIVE_LIMIT;
}

export function shouldCompactTransientVfxSlots(trackedTransientSlotCount: number): boolean {
  return trackedTransientSlotCount > TRANSIENT_VFX_SLOT_COMPACTION_LIMIT;
}

export function resolveTransientVfxPeakActiveCount({
  currentPeakActiveTransientCount,
  activeTransientCount
}: ResolveTransientVfxPeakActiveCountInput): number {
  return Math.max(currentPeakActiveTransientCount, activeTransientCount);
}

export function resolveSceneVfxDiagnosticsSnapshot({
  activeTransientCount,
  trackedTransientSlotCount,
  activeRingCount,
  counters
}: ResolveSceneVfxDiagnosticsSnapshotInput): SceneVfxDiagnosticsSnapshot {
  return {
    activeTransientCount,
    trackedTransientSlotCount,
    activeRingCount,
    createdCount: counters.createdCount,
    destroyedCount: counters.destroyedCount,
    peakActiveTransientCount: counters.peakActiveTransientCount
  };
}
