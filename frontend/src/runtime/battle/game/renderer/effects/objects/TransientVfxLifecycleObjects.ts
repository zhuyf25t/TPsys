import type Phaser from "phaser";

export interface TransientEffectRecord {
  object: Phaser.GameObjects.GameObject;
  active: boolean;
}

export interface SceneVfxDiagnosticsSnapshot {
  activeTransientCount: number;
  trackedTransientSlotCount: number;
  activeRingCount: number;
  createdCount: number;
  destroyedCount: number;
  peakActiveTransientCount: number;
}

export interface SlayDemoBattleDiagnosticsRoot {
  vfx?: SceneVfxDiagnosticsSnapshot;
  [key: string]: unknown;
}

export interface TransientVfxLifecycleOptions {
  getActiveRingCount: () => number;
}

export interface TransientVfxDestroyAllOptions {
  publishDiagnostics?: boolean;
}

export interface TransientVfxDiagnosticsCounters {
  createdCount: number;
  destroyedCount: number;
  peakActiveTransientCount: number;
}

export interface ResolveSceneVfxDiagnosticsSnapshotInput {
  activeTransientCount: number;
  trackedTransientSlotCount: number;
  activeRingCount: number;
  counters: TransientVfxDiagnosticsCounters;
}

export interface ResolveTransientVfxPeakActiveCountInput {
  currentPeakActiveTransientCount: number;
  activeTransientCount: number;
}
