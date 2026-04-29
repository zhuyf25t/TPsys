import type Phaser from "phaser";
import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "../battleDiagnosticsGate";

interface TransientEffectRecord {
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

interface SlayDemoBattleDiagnosticsRoot {
  vfx?: SceneVfxDiagnosticsSnapshot;
  [key: string]: unknown;
}

export interface TransientVfxLifecycleOptions {
  getActiveRingCount: () => number;
}

const MAX_TRANSIENT_VFX = 120;
const TRANSIENT_COMPACTION_LIMIT = MAX_TRANSIENT_VFX * 2;

export class TransientVfxLifecycle {
  private transientEffects: TransientEffectRecord[] = [];
  private transientEffectRecords = new Map<Phaser.GameObjects.GameObject, TransientEffectRecord>();
  private transientActiveCount = 0;
  private transientHeadIndex = 0;
  private readonly diagnosticsEnabled = isBattleDiagnosticsEnabled();
  private diagnosticsCreatedCount = 0;
  private diagnosticsDestroyedCount = 0;
  private diagnosticsPeakActiveTransientCount = 0;

  public constructor(private readonly options: TransientVfxLifecycleOptions) {
    if (this.diagnosticsEnabled) {
      this.publishDiagnostics();
    }
  }

  public get activeCount(): number {
    return this.transientActiveCount;
  }

  public get slotCount(): number {
    return this.transientEffects.length;
  }

  public get createdCount(): number {
    return this.diagnosticsCreatedCount;
  }

  public get destroyedCount(): number {
    return this.diagnosticsDestroyedCount;
  }

  public get peakActiveCount(): number {
    return this.diagnosticsPeakActiveTransientCount;
  }

  public track<TObject extends Phaser.GameObjects.GameObject>(object: TObject): TObject {
    const record: TransientEffectRecord = { object, active: true };
    this.transientEffects.push(record);
    this.transientEffectRecords.set(object, record);
    this.transientActiveCount += 1;
    if (this.diagnosticsEnabled) {
      this.diagnosticsCreatedCount += 1;
    }

    while (this.transientActiveCount > MAX_TRANSIENT_VFX) {
      this.destroyOldestTransient();
    }

    this.maybeCompactTransientEffects();
    this.updatePeakActiveTransientDiagnostics();
    if (this.diagnosticsEnabled) {
      this.publishDiagnostics();
    }
    return object;
  }

  public destroyObject(object: Phaser.GameObjects.GameObject): void {
    const record = this.transientEffectRecords.get(object);
    if (!record) {
      return;
    }

    this.releaseTransient(record);
    this.trimTransientHead();
    this.maybeCompactTransientEffects();
    if (this.diagnosticsEnabled) {
      this.publishDiagnostics();
    }
  }

  public destroyAll(options: { publishDiagnostics?: boolean } = {}): void {
    if (this.diagnosticsEnabled) {
      this.diagnosticsDestroyedCount += this.transientActiveCount;
    }
    this.transientEffects.forEach((effect) => effect.object.destroy());
    this.transientEffects = [];
    this.transientEffectRecords.clear();
    this.transientActiveCount = 0;
    this.transientHeadIndex = 0;
    if (this.diagnosticsEnabled && (options.publishDiagnostics ?? true)) {
      this.publishDiagnostics();
    }
  }

  public publishDiagnostics(): void {
    if (!this.diagnosticsEnabled) {
      return;
    }

    const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoBattleDiagnosticsRoot>();
    if (!diagnosticsRoot) {
      return;
    }

    diagnosticsRoot.vfx = {
      activeTransientCount: this.transientActiveCount,
      trackedTransientSlotCount: this.transientEffects.length,
      activeRingCount: this.options.getActiveRingCount(),
      createdCount: this.diagnosticsCreatedCount,
      destroyedCount: this.diagnosticsDestroyedCount,
      peakActiveTransientCount: this.diagnosticsPeakActiveTransientCount
    };
  }

  private destroyOldestTransient(): void {
    while (this.transientHeadIndex < this.transientEffects.length) {
      const record = this.transientEffects[this.transientHeadIndex];
      this.transientHeadIndex += 1;

      if (!record.active) {
        continue;
      }

      this.releaseTransient(record);
      this.trimTransientHead();
      return;
    }

    this.transientEffects = [];
    this.transientEffectRecords.clear();
    this.transientActiveCount = 0;
    this.transientHeadIndex = 0;
    if (this.diagnosticsEnabled) {
      this.publishDiagnostics();
    }
  }

  private releaseTransient(record: TransientEffectRecord): void {
    if (!record.active) {
      return;
    }

    record.active = false;
    this.transientEffectRecords.delete(record.object);
    this.transientActiveCount -= 1;
    if (this.diagnosticsEnabled) {
      this.diagnosticsDestroyedCount += 1;
    }
    record.object.destroy();
  }

  private trimTransientHead(): void {
    if (this.transientActiveCount <= 0) {
      this.transientEffects = [];
      this.transientEffectRecords.clear();
      this.transientActiveCount = 0;
      this.transientHeadIndex = 0;
      return;
    }

    while (
      this.transientHeadIndex < this.transientEffects.length &&
      !this.transientEffects[this.transientHeadIndex].active
    ) {
      this.transientHeadIndex += 1;
    }
  }

  private maybeCompactTransientEffects(): void {
    if (this.transientEffects.length <= TRANSIENT_COMPACTION_LIMIT) {
      return;
    }

    const compacted: TransientEffectRecord[] = [];

    for (let index = this.transientHeadIndex; index < this.transientEffects.length; index += 1) {
      const record = this.transientEffects[index];
      if (!record.active) {
        continue;
      }

      if (!record.object.active) {
        this.releaseTransient(record);
        continue;
      }

      compacted.push(record);
    }

    this.transientEffects = compacted;
    this.transientHeadIndex = 0;
  }

  private updatePeakActiveTransientDiagnostics(): void {
    if (!this.diagnosticsEnabled) {
      return;
    }

    this.diagnosticsPeakActiveTransientCount = Math.max(
      this.diagnosticsPeakActiveTransientCount,
      this.transientActiveCount
    );
  }
}
